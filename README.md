# GoldProxy

This repository contains a multi-module Maven scaffold for GoldProxy: a Velocity proxy plugin (goldproxy-proxy) and a companion Paper backend plugin (goldproxy-paper). The goldproxy-common module contains shared DTOs and messaging classes.

What is included:
- Basic multi-module Maven project (parent pom + module poms)
- velocity-plugin.json and paper plugin.yml
- Java skeletons for proxy main class, basic /ping and /msg command skeletons, Paper backend listener, and a PM message DTO
- Basic config template

How to build:
- Install Maven and JDK 17
- From repo root run: mvn -DskipTests package

Next steps I can implement for you:
- Full Redis pub/sub wiring for cross-server PMs and state sync
- Maintenance system with dynamic MOTD and login filtering
- Protocol blocking and hackclient reporting
- Auto-messages scheduler

Tell me which feature to implement next or I can implement the Redis-backed cross-server messaging and maintenance system next and push updates to this branch.
