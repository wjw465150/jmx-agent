jmx-agent
=========
jmx-agent uses the JVM Agent interface for linking into any JVM,and export JMX Server!

Installation
=========
This agent gets installed by providing a single startup option -javaagent when starting the Java process.

> java -javaagent:JmxAgent.jar=port=5678,host=localhost

JmxAgent.jar is the filename of the JMX JVM agent. Options can be appended as a comma separated list. 

> `host` Hostaddress to which the JMX server should bind to. If "*" or "0.0.0.0" is given, the servers binds to every network interface.defaule is`localhost`
			
> `port` Port the JMX server should listen to. defaule is`5678`

> `user` User to be used for authentication (along with a password)

> `password` Password used for authentication (user is then required, too)

Upon sucessful startup the agent will print out a success message with the full URL which can be used by clients for contacting the JMX Agent.