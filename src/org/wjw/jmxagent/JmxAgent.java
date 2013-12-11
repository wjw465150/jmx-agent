package org.wjw.jmxagent;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

public class JmxAgent {

  private JmxAgent() {
  }

  /**
   * Entry point for the agent, using command line attach (that is via
   * -javaagent command line argument)
   * 
   * @param agentArgs
   *          arguments as given on the command line
   */
  public static void premain(String agentArgs) {
    if (System.getProperty("user.dir").startsWith(".")) { //把user.dir变成绝对路径
      File ff = new File(System.getProperty("user.dir"));
      try {
        System.setProperty("user.dir", ff.getCanonicalPath());
      } catch (IOException e) {
        //e.printStackTrace();
      }
    }

    Map<String, String> argsMap = split(agentArgs);
    startAgent(argsMap);
  }

  private static void startAgent(Map<String, String> argsMap) {
    int jmxPort = 5678;
    String jmxHost = "127.0.0.1";
    try {
      String strJmxPort = argsMap.get("port");
      if (strJmxPort != null) {
        try {
          jmxPort = Integer.parseInt(strJmxPort);
        } catch (Exception e) {
          jmxPort = 5678;
          e.printStackTrace(System.err);
        }
      }

      if (argsMap.containsKey("host")) {
        jmxHost = argsMap.get("host");
      }

      Map<String, Object> env = null;
      if (argsMap.get("user") != null && argsMap.get("password") != null) {
        final String jmxUser = argsMap.get("user");
        final String jmxPassword = argsMap.get("password");
        env = new HashMap<String, Object>();
        env.put(JMXConnectorServer.AUTHENTICATOR, new JMXAuthenticator() {

          public Subject authenticate(Object credentials) {
            String[] sCredentials = (String[]) credentials;
            if (jmxUser.equals(sCredentials[0]) && jmxPassword.equals(sCredentials[1])) {
              Set<Principal> principals = new HashSet<Principal>();
              principals.add(new JMXPrincipal(jmxUser));
              return new Subject(true, principals, Collections.EMPTY_SET, Collections.EMPTY_SET);
            }
            throw new SecurityException("JMX Connect Authentication failed! ");

          }
        });
      }

      final String localHostname = InetAddress.getLocalHost().getHostName();
      LocateRegistry.createRegistry(jmxPort);
      System.out.println("Getting the platform's MBean Server");
      MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

      JMXServiceURL localUrl = new JMXServiceURL("service:jmx:rmi://" + localHostname + ":" + 
          jmxPort + "/jndi/rmi://" + localHostname + ":" + 
          jmxPort + "/jmxrmi");

      JMXServiceURL hostUrl = new JMXServiceURL("service:jmx:rmi://" + jmxHost + ":" + 
          jmxPort + "/jndi/rmi://" + jmxHost + ":" + 
          jmxPort + "/jmxrmi");
      

      System.out.println("InetAddress.getLocalHost().getHostName() Connection URL: " + localUrl);
      System.out.println("Used host Connection URL: " + hostUrl);
      System.out.println("Creating RMI connector server");
      JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(hostUrl, env, mbs); 
      cs.start();

      CleanupThread cleaner = new CleanupThread(cs);
      cleaner.start();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }

  //Split arguments into a map
  private static Map<String, String> split(String pAgentArgs) {
    Map<String, String> ret = new HashMap<String, String>();
    if (pAgentArgs != null && pAgentArgs.length() > 0) {
      for (String arg : EscapeUtil.splitAsArray(pAgentArgs, EscapeUtil.CSV_ESCAPE, ",")) {
        String[] prop = EscapeUtil.splitAsArray(arg, EscapeUtil.CSV_ESCAPE, "=");
        if (prop == null || prop.length != 2) {
          ret.put("port", "5678");
        } else {
          ret.put(prop[0], prop[1]);
        }
      }
    }
    return ret;
  }
}
