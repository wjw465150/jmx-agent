package org.wjw.jmxagent;

import javax.management.remote.JMXConnectorServer;

/**
 * Thread for stopping the JMXConnectorServer as soon as every non-daemon thread
 * has exited. This thread was inspired by the ideas from Daniel Fuchs (although
 * the implementation is different)
 * (http://blogs.sun.com/jmxetc/entry/more_on_premain_and_jmx)
 * 
 * @author roland
 * @since Mar 3, 2010
 */
class CleanupThread extends Thread {

  private JMXConnectorServer server;

  CleanupThread(JMXConnectorServer pServer) {
    super("JmxAgent Cleaner");
    server = pServer;
    setDaemon(true);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    boolean loop = true;
    try {
      while (loop) {
        final Thread[] all = enumerateThreads();
        loop = false;
        for (int i = 0; i < all.length; i++) {
          final Thread t = all[i];
          // daemon: skip it.
          if (t.isDaemon())
            continue;

          // RMI Reaper: skip it.
          if (t.getName().startsWith("RMI Reaper"))
            continue;
          if (t.getName().startsWith("DestroyJavaVM"))
            continue;

          // Tanuki Java Service Wrapper
          if (t.getName().startsWith("WrapperListener_stop_runner"))
            continue;

          // Non daemon, non RMI Reaper: join it, break the for
          // loop, continue in the while loop (loop=true)
          loop = true;
          try {
            System.out.println("JmxAgent CleanupThread Waiting on " + t.getName() + " [id=" + t.getId() + "]");
            t.join();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
          break;
        }
      }
      // We went through a whole for-loop without finding any thread
      // to join. We can close cs.
    } catch (Exception ex) {
      ex.printStackTrace();
    } finally {
      try {
        // if we reach here it means the only non-daemon threads
        // that remain are reaper threads - or that we got an
        // unexpected exception/error.
        //
        System.out.println("JmxAgent CleanupThread Stop JMXConnectorServer!");
        server.stop();
      } catch (Exception ex) {
        //ex.printStackTrace();
      }
    }
  }

  // Enumerate all active threads
  private Thread[] enumerateThreads() {
    boolean fits = false;
    int inc = 50;
    Thread[] threads = null;
    int nrThreads = 0;
    while (!fits) {
      try {
        threads = new Thread[Thread.activeCount() + inc];
        nrThreads = Thread.enumerate(threads);
        fits = true;
      } catch (ArrayIndexOutOfBoundsException exp) {
        inc += 50;
      }
    }
    // Trim array
    Thread ret[] = new Thread[nrThreads];
    System.arraycopy(threads, 0, ret, 0, nrThreads);
    return ret;
  }

}
