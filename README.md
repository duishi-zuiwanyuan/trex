## Trex: An embeddable Paxos engine for the JVM

Checkout the [GitHub pages] (http://trex-paxos.github.io/trex/) for more information else the wiki here on Github.

[![Build Status](https://travis-ci.org/trex-paxos/trex.svg?branch=master)](https://travis-ci.org/trex-paxos/trex)
[![Codacy Badge](https://www.codacy.com/project/badge/73b345d5a4c74a4d9d458596e64fe212)](https://www.codacy.com/app/simbo1905remixed/trex)
[![Join the chat at https://gitter.im/trex-paxos/trex](https://badges.gitter.im/trex-paxos/trex.svg)](https://gitter.im/trex-paxos/trex?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Releases

[Trex 0.1](https://github.com/trex-paxos/trex/tree/1.0) is now released to [Central] (http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.trex-paxos%22)! This release has what is believed to be a correct and [functional paxos library] (http://search.maven.org/#artifactdetails%7Ccom.github.trex-paxos%7Ctrex-library_2.11%7C0.1%7Cjar). The other jars ( `core` server and `demo` module) are only enough to run simple demos. A key missing features is that `core` has no logic for dynamic cluster membership.  

## Building

```
# Kick the tires
sbt clean coverage test it:test
sbt coverageReport
```

## Status /  Work Plan

0.1 - library (released)

- [x] replace pickling
- [x] fix driver
- [x] is retransmission of accepted values actually safe?
- [x] overrideable send methods
- [x] fix the fixmes
- [x] extract a core functional library with no dependencies
- [x] breakup monolithic actor and increase unit test coverage
- [x] java demo

0.6 - practical

- [ ] dynamic cluster membership
- [ ] forced reconfigurations
- [ ] learners / scale-out multicast
- [ ] timeline reads
- [ ] noop heartbeats (less duels and partitioned leader detection)
- [ ] snapshots and out of band retransmission
- [ ] metrics/akka-tracing
- [ ] binary tracing 
- [ ] jumbo UDP packets
- [ ] complete the TODOs

0.7 - performance

- [ ] strong reads
- [ ] outdated reads
- [ ] optimised journal 
- [ ] batching 
- [ ] remove remote actor from client driver
- [ ] replica strong reads
- [ ] compression 
- [ ] journal truncation by size 
- [ ] periodically leader number boosting

0.8 

- [ ] final API
- [ ] hand-off reads? 

M1

- [ ] transaction demo
- [ ] ???

## Attribution

The TRex icon is Tyrannosaurus Rex by Raf Verbraeken from the Noun Project licensed under [CC3.0](http://creativecommons.org/licenses/by/3.0/us/)

