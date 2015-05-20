package org.wjw.jmxagent;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;

public class JmxServer {

  private final String RMI_SERVER_HOST_NAME_PROPERTY = "java.rmi.server.hostname";

  private Registry rmiRegistry;
  private InetAddress inetAddress;
  private int serverPort;
  private int registryPort;
  private JMXConnectorServer connector;
  private MBeanServer mbeanServer;
  private RMIServerSocketFactory serverSocketFactory;
  private boolean serverHostNamePropertySet = false;
  private String serviceUrl;

  /**
   * Create a JMX server running on a particular registry and server port pair.
   * 
   * @param registryPort
   *          The "RMI registry port" that you specify in jconsole to connect to
   *          the server. See {@link #setRegistryPort(int)}.
   * @param serverPort
   *          The RMI server port that jconsole uses to transfer data to/from
   *          the server. See {@link #setServerPort(int)}. The same port as the
   *          registry-port can be used.
   */
  public JmxServer(int registryPort, int serverPort) {
    this.registryPort = registryPort;
    this.serverPort = serverPort;
  }

  /**
   * Create a JMX server running on a particular registry and server port pair.
   * 
   * @param inetAddress
   *          Address to bind to. If you use on the non-address constructors, it
   *          will bind to all interfaces.
   * @param registryPort
   *          The "RMI registry port" that you specify in jconsole to connect to
   *          the server. See {@link #setRegistryPort(int)}.
   * @param serverPort
   *          The RMI server port that jconsole uses to transfer data to/from
   *          the server. See {@link #setServerPort(int)}. The same port as the
   *          registry-port can be used.
   */
  public JmxServer(InetAddress inetAddress, int registryPort, int serverPort) {
    this.inetAddress = inetAddress;
    this.registryPort = registryPort;
    this.serverPort = serverPort;
  }

  /**
   * Start our JMX service. The port must have already been called either in the
   * {@link #JmxServer(int)} constructor or the {@link #setRegistryPort(int)}
   * method before this is called.
   * 
   * @throws IllegalStateException
   *           If the registry port has not already been set.
   */
  public synchronized void start() throws JMException {
    if (mbeanServer != null) {
      // if we've already assigned a mbean-server then there's nothing to start
      return;
    }
    if (registryPort == 0) {
      throw new IllegalStateException("registry-port must be already set when JmxServer is initialized");
    }
    startRmiRegistry();
    startJmxService();
  }

  /**
   * Same as {@link #stopThrow()} but this ignores any exceptions.
   */
  public synchronized void stop() {
    try {
      stopThrow();
    } catch (JMException e) {
      // ignored
    }
  }

  /**
   * Stop the JMX server by closing the connector and unpublishing it from the
   * RMI registry. This throws a JMException on any issues.
   */
  public synchronized void stopThrow() throws JMException {
    if (connector != null) {
      try {
        connector.stop();
      } catch (IOException e) {
        throw createJmException("Could not stop our Jmx connector server", e);
      } finally {
        connector = null;
      }
    }
    if (rmiRegistry != null) {
      try {
        UnicastRemoteObject.unexportObject(rmiRegistry, true);
      } catch (NoSuchObjectException e) {
        throw createJmException("Could not unexport our RMI registry", e);
      } finally {
        rmiRegistry = null;
      }
    }
    if (serverHostNamePropertySet) {
      System.clearProperty(RMI_SERVER_HOST_NAME_PROPERTY);
      serverHostNamePropertySet = false;
    }
  }

  /**
   * @see JmxServer#setRegistryPort(int)
   */
  public int getRegistryPort() {
    return registryPort;
  }

  /**
   * @see JmxServer#setServerPort(int)
   */
  public int getServerPort() {
    return serverPort;
  }

  private void startRmiRegistry() throws JMException {
    if (rmiRegistry != null) {
      return;
    }
    try {
      if (inetAddress == null) {
        rmiRegistry = LocateRegistry.createRegistry(registryPort);
      } else {
        if (serverSocketFactory == null) {
          serverSocketFactory = new LocalSocketFactory(inetAddress);
        }
        if (System.getProperty(RMI_SERVER_HOST_NAME_PROPERTY) == null) {
          /*
           * We have to do this because JMX tries to connect back the server
           * that we just set and it won't be able to locate it if we set our
           * own address to anything but the InetAddress.getLocalHost() address.
           */
          System.setProperty(RMI_SERVER_HOST_NAME_PROPERTY, inetAddress.getHostAddress());
          serverHostNamePropertySet = true;
        }
        /*
         * NOTE: the client factory being null is a critical part of this for
         * some reason. If we specify a client socket factory then the registry
         * and the RMI server can't be on the same port. Thanks to EJB.
         * 
         * I also tried to inject a client socket factory both here and below in
         * the connector environment but I could not get it to work.
         */
        rmiRegistry = LocateRegistry.createRegistry(registryPort, null, serverSocketFactory);
      }
    } catch (IOException e) {
      throw createJmException("Unable to create RMI registry on port " + registryPort, e);
    }
  }

  private void startJmxService() throws JMException {
    if (connector != null) {
      return;
    }
    if (serverPort == 0) {
      /*
       * If we aren't specifying an address then we can use the registry-port
       * for both the registry call _and_ the RMI calls. There is RMI port
       * multiplexing underneath the covers of the JMX handler. Did not know
       * that. Thanks to EJB.
       */
      serverPort = registryPort;
    }
    String serverHost = "localhost";
    String registryHost = "";
    if (inetAddress != null) {
      String hostAddr = inetAddress.getHostAddress();
      serverHost = hostAddr;
      registryHost = hostAddr;
    }
    if (serviceUrl == null) {
      serviceUrl = "service:jmx:rmi://" + serverHost + ":" + serverPort + "/jndi/rmi://" + registryHost + ":"
          + registryPort + "/jmxrmi";
    }
    JMXServiceURL url;
    try {
      url = new JMXServiceURL(serviceUrl);
    } catch (MalformedURLException e) {
      throw createJmException("Malformed service url created " + serviceUrl, e);
    }

    Map<String, Object> envMap = null;
    if (serverSocketFactory != null) {
      envMap = new HashMap<String, Object>();
      envMap.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, serverSocketFactory);
    }
    /*
     * NOTE: I tried to inject a client socket factory with
     * RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE but I could not
     * get it to work. It seemed to require the client to have the
     * LocalSocketFactory class in the classpath.
     */

    try {
      mbeanServer = ManagementFactory.getPlatformMBeanServer();
      connector = JMXConnectorServerFactory.newJMXConnectorServer(url, envMap, mbeanServer);
    } catch (IOException e) {
      throw createJmException("Could not make our Jmx connector server on URL: " + url, e);
    }
    try {
      connector.start();
    } catch (IOException e) {
      connector = null;
      throw createJmException("Could not start our Jmx connector server on URL: " + url, e);
    }
  }

  private JMException createJmException(String message, Exception e) {
    JMException jmException = new JMException(message);
    jmException.initCause(e);
    return jmException;
  }

  /**
   * Socket factory which allows us to set a particular local address.
   */
  private static class LocalSocketFactory implements RMIServerSocketFactory {

    private final InetAddress inetAddress;

    public LocalSocketFactory(InetAddress inetAddress) {
      this.inetAddress = inetAddress;
    }

    public ServerSocket createServerSocket(int port) throws IOException {
      return new ServerSocket(port, 0, inetAddress);
    }

    @Override
    public int hashCode() {
      return (this.inetAddress == null ? 0 : this.inetAddress.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      LocalSocketFactory other = (LocalSocketFactory) obj;
      if (this.inetAddress == null) {
        return (other.inetAddress == null);
      } else {
        return this.inetAddress.equals(other.inetAddress);
      }
    }
  }
}