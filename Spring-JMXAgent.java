/**
 * 
 */
package org.springframework.dmserver.jmxagent;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

/**
 * @author Oleg Zhurakousky
 *
 */
public class JMXAgent {
	public static final String RMI_REGISTRY_PORT = "com.springsource.rmiregistry.port";
	public static final String RMI_SERVER_CONNECTION_PORT = "com.springsource.rmiserver.port";
	private JMXAgent(){}
	/**
	 * 
	 * @param agentArgs
	 */  
	public static void premain(String agentArgs) throws Throwable {
		final int rmiRegistryPort= Integer.parseInt(System.getProperty(RMI_REGISTRY_PORT,"44444"));	
		final int rmiServerPort= Integer.parseInt(System.getProperty(RMI_SERVER_CONNECTION_PORT, (rmiRegistryPort+1)+""));
		final String hostname = InetAddress.getLocalHost().getHostName();
		final String publicHostName = System.getProperty("java.rmi.server.hostname", hostname);
		
		System.out.println(RMI_REGISTRY_PORT + ":" + rmiRegistryPort);
		System.out.println(RMI_SERVER_CONNECTION_PORT + ":" + rmiServerPort);
		
		LocateRegistry.createRegistry(rmiRegistryPort);
		
		System.out.println("Getting the platform's MBean Server");
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		
		Map<String, Object> env = new HashMap<String, Object>();
		// Provide SSL-based RMI socket factories.
        //
        // The protocol and cipher suites to be enabled will be the ones
        // defined by the default JSSE implementation and only server
        // authentication will be required.
        //
        SslRMIClientSocketFactory csf = new SslRMIClientSocketFactory();
        SslRMIServerSocketFactory ssf = new SslRMIServerSocketFactory();
        env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, csf);
        env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, ssf);

        // Provide the password file used by the connector server to
        // perform user authentication. The password file is a properties
        // based text file specifying username/password pairs.
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://" + hostname + ":" + 
												rmiServerPort + "/jndi/rmi://" + hostname + ":" + 
												rmiRegistryPort + "/jmxrmi");
        // Used only to dosplay what the public address should be
        JMXServiceURL publicUrl = new JMXServiceURL("service:jmx:rmi://" + publicHostName + ":" + 
				rmiServerPort + "/jndi/rmi://" + publicHostName + ":" + 
				rmiRegistryPort + "/jmxrmi");
		
        System.out.println("Local Connection URL: " + url);
		System.out.println("Public Connection URL: " + publicUrl);
		System.out.println("Creating RMI connector server");
		JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);	
		cs.start();
	}
}
