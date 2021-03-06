package com.github.trex_paxos.library

case class PaxosAgent(nodeUniqueId: Int, role: PaxosRole, data: PaxosData)

case class PaxosEvent(io: PaxosIO, agent: PaxosAgent, message: PaxosMessage)

trait PaxosIO {
  def journal: Journal

  def logger: PaxosLogging

  def randomTimeout: Long

  def clock: Long

  def deliver(payload: Payload): Any

  def send(msg: PaxosMessage)

  def minPrepare: Prepare

  // actor version side-effects by adding id to a weak map
  def senderId(): String

  def respond(client: String, data: Any)

  def sendNoLongerLeader(clientCommands: Map[Identifier, (CommandValue, String)]): Unit
}

object PaxosAlgorithm {
  type PaxosFunction = PartialFunction[PaxosEvent, PaxosAgent]
}

class PaxosAlgorithm extends PaxosLenses
with CommitHandler
with FollowerHandler
with RetransmitHandler
with PrepareHandler
with AcceptHandler
with PrepareResponseHandler
with AcceptResponseHandler
with ResendHandler
with ReturnToFollowerHandler
with ClientCommandHandler {

  import PaxosAlgorithm._

  val followingFunction: PaxosFunction = {
    // update heartbeat and attempt to commit contiguous accept messages
    case PaxosEvent(io, agent@PaxosAgent(_, Follower, _), c@Commit(i, heartbeat)) =>
      handleFollowerCommit(io, agent, c)
    case PaxosEvent(io, agent@PaxosAgent(_, Follower, PaxosData(_, _, to, _, _, _, _, _)), CheckTimeout) if io.clock >= to =>
      handleFollowerTimeout(io, agent)
    case PaxosEvent(io, agent, vote: PrepareResponse) if agent.role == Follower =>
      handelFollowerPrepareResponse(io, agent, vote)
    // ignore an accept response which may be seen after we backdown to follower
    case PaxosEvent(_, agent@PaxosAgent(_, Follower, _), vote: AcceptResponse) =>
      agent
  }

  val retransmissionStateFunction: PaxosFunction = {
    case PaxosEvent(io, agent, rq: RetransmitRequest) =>
      handleRetransmitRequest(io, agent, rq)

    case PaxosEvent(io, agent, rs: RetransmitResponse) =>
      handleRetransmitResponse(io, agent, rs)
  }

  val prepareStateFunction: PaxosFunction = {
    case PaxosEvent(io, agent, p@Prepare(id)) =>
      handlePrepare(io, agent, p)
  }

  val acceptStateFunction: PaxosFunction = {
    case PaxosEvent(io, agent, a: Accept) =>
      handleAccept(io, agent, a)
  }

  val ignoreHeartbeatStateFunction: PaxosFunction = {
    // ingore a HeartBeat which has not already been handled
    case PaxosEvent(io, agent, HeartBeat) =>
      agent
  }

  val unknown: PaxosFunction = {
    case PaxosEvent(io, agent, x) =>
      io.logger.warning("unknown message {}", x)
      agent
  }

  /**
   * If no other logic has caught a timeout then do nothing.
   */
  val ignoreNotTimedOutCheck: PaxosFunction = {
    case PaxosEvent(_, agent, CheckTimeout) =>
      agent
  }

  val commonStateFunction: PaxosFunction =
    retransmissionStateFunction orElse
      prepareStateFunction orElse
      acceptStateFunction orElse
      ignoreHeartbeatStateFunction orElse
      ignoreNotTimedOutCheck orElse
      unknown

  val notLeaderFunction: PaxosFunction = {
    case PaxosEvent(io, agent, v: CommandValue) =>
      io.send(NotLeader(agent.nodeUniqueId, v.msgId))
      agent
  }

  val followerFunction: PaxosFunction = followingFunction orElse notLeaderFunction orElse commonStateFunction

  val takeoverFunction: PaxosFunction = {
    case PaxosEvent(io, agent, vote: PrepareResponse) =>
      handlePrepareResponse(io, agent, vote)
  }

  val acceptResponseFunction: PaxosFunction = {
    case PaxosEvent(io, agent, vote: AcceptResponse) =>
      handleAcceptResponse(io, agent, vote)
  }

  /**
   * Here on a timeout we deal with either pending prepares or pending accepts putting a priority on prepare handling
   * which backs down easily. Only if we have dealt with all timed out prepares do we handle timed out accepts which
   * is more aggressive as it attempts to go-higher than any other node number.
   */
  val resendFunction: PaxosFunction = {
    // if we have timed-out on prepare messages
    case PaxosEvent(io, agent, CheckTimeout) if agent.data.prepareResponses.nonEmpty && io.clock > agent.data.timeout =>
      handleResendPrepares(io, agent, io.clock)

    // if we have timed-out on accept messages
    case PaxosEvent(io, agent, CheckTimeout) if agent.data.acceptResponses.nonEmpty && io.clock >= agent.data.timeout =>
      handleResendAccepts(io, agent, io.clock)
  }

  val leaderLikeFunction: PaxosFunction = {
    case PaxosEvent(io, agent, c: Commit) =>
      handleReturnToFollowerOnHigherCommit(io, agent, c)
  }

  val recoveringFunction: PaxosFunction =
    takeoverFunction orElse
      acceptResponseFunction orElse
      resendFunction orElse
      leaderLikeFunction orElse
      notLeaderFunction orElse
      commonStateFunction

  val recovererFunction: PaxosFunction = recoveringFunction orElse notLeaderFunction orElse commonStateFunction

  val leaderStateFunction: PaxosFunction = {
    // heartbeats the highest commit message
    case PaxosEvent(io, agent, HeartBeat) =>
      io.send(Commit(agent.data.progress.highestCommitted))
      agent

    // broadcasts a new client value
    case PaxosEvent(io, agent, value: CommandValue) =>
      handleClientCommand(io, agent, value, io.senderId)

    // ignore late vote as we would have transitioned on a majority ack
    case PaxosEvent(io, agent, value: PrepareResponse) =>
      agent
  }

  val leaderFunction: PaxosFunction =
    leaderStateFunction orElse
      acceptResponseFunction orElse
      resendFunction orElse
      leaderLikeFunction orElse
      commonStateFunction

  def apply(e: PaxosEvent): PaxosAgent = e.agent.role match {
    case Follower => followerFunction(e)
    case Recoverer => recovererFunction(e)
    case Leader => leaderFunction(e)
  }
}
