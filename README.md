diff --git a/README.md b/README.md
index e69de29..0000000 100644
--- a/README.md
+++ b/README.md
@@ -1,7 +1,35 @@
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
 
+Transport configuration
+-----------------------
+
+You can choose between Redis and plugin-message transports (or enable both). The proxy and backend each have a config.yml in their resources that include a transports section. Example options:
+
+- transports.redis.enabled: true/false
+- transports.plugin-message.enabled: true/false
+- transports.plugin-message.channel: goldproxy:main
+- transports.plugin-message.shared-secret: a-shared-secret-used-for-basic-auth
+
+If Redis is enabled both proxy and backend will attempt to connect to the configured Redis host/port. If plugin-message is enabled, the backend will attempt to send plugin messages (using online players as carriers) and the proxy will register plugin message handling as well.
+
+The initial implementation includes config templates and transport manager stubs. I can fully implement Redis pub/sub routing and the plugin message codec next.
+
