package se.bricole.xss.server;

import java.net.*;
import java.io.*;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Iterator;

import javax.swing.WindowConstants;
import javax.swing.JFrame;
import java.awt.Font;
import javax.swing.JScrollPane;
import java.awt.event.*;

/**
 * This is the class containing the "main" method, and is responsible for accepting and
 * allocating incoming connections as well as garbage-collecting old ClientProxies and
 * idle clients.
 *
 * @version $Id$
 */
public class Server implements SessionEventListener, Runnable, ServerManager {

    public final static String vcId = "$Id$";

    public final static int VERSION_MAJOR = 0;
    public final static int VERSION_MINOR = 9;

    public final static String PRODUCT_NAME = "XSS";

    static long bootTime = 0;


    String configFile;

    int maxProxies;
    int usersPerProxy;

    int idleCount = 0, clientCount = 0;

    int proxyCounter = 0;
    static int clientCounter = 0;

    boolean runGC = true;
    Thread gcThread = null;
    Vector proxies = new Vector();
    ClientProxy proxy = null;

    ClientSession[] threadPool;
    boolean growableThreadPool;
    int oneShotThreads = 4;

    Set looseThreads = Collections.synchronizedSet(new HashSet());
    public boolean shutdown = false;
        
    ServerSocket srv;

    public final static boolean debug = Boolean.getBoolean("xss.debug");

    /**
     * Returns a version string in the format "major.minor"
     */
    public static String getVersionString() {
	return VERSION_MAJOR + "." + VERSION_MINOR;
    }

    /**
     * This function returns globally unique ID's for the server,
     * used for the ClientConnection ID's.
     */
    public static synchronized int getGUID() {
	return clientCounter++;
    }

    public void gc() {
	_gc();
    }

    protected void _gc() {
	boolean printStatus = config.getIntProperty("PrintStatus") == 1 ? true : false;
	long clientIdleTimeout = config.getIntProperty("ClientIdleTimeout") * 1000;

	// fill up empty slots in the thread pool.
	// this might not be the best way to keep the pooled threads
	// alive, but it works for now. I can imagine this to be quite
	// inefficient with a low GC interval and high InitialThreadPool
	int filled = 0, alive = 0, active = 0;
	for (int i = 0; i < threadPool.length; i++) {
	    if (threadPool[i] != null && threadPool[i].isAlive()) {
		alive++;
		if (threadPool[i].isActive())
		    active++;
	    } else {
		threadPool[i] = new ClientSession(srv, this);
		threadPool[i].setPoolSlot(i);
		threadPool[i].setDaemon(false);
		threadPool[i].start();
		filled++;
		try {
		    Thread.sleep(200);
		    // sleep one fifth of a second to prevent bogging the server
		} catch (InterruptedException interruptedException) {
		}
	    }
	}
	if (filled > 0)
	    debug("Filled " + filled + " void slots in thread pool");

	if (proxies.size() != 0) {
	    if (printStatus)
		status(proxies.size() + " proxies active, uptime is " + getUptime());

	    Vector v = new Vector();
	    Vector idleClients = new Vector();
	    Enumeration e = proxies.elements();

	    while (e.hasMoreElements()) { // XXX: synchronization? oh, it's a Vector...
		ClientProxy p = (ClientProxy) e.nextElement();
		if (p.getClientCount() == 0) {
		    if (printStatus)
			status("proxy " + p.toString() + " has no clients, will remove");
		    v.add(p);
		} else {
		    Iterator ci = p.getClients();
		    while (ci.hasNext()) {
			ClientSession cc = (ClientSession) ci.next();
			if (cc.isActive() && cc.getIdleTimeMillis() > clientIdleTimeout) {
			    idleClients.add(cc);
			}
		    }
		    if (printStatus)
			status(p.toString()
			       + ": clients: "
			       + p.getClientCount()
			       + ", broadcasts: "
			       + p.getBroadcastCount()
			       + ", commands: "
			       + p.getCommandCount()); 
		}
	    }

	    e = v.elements();
	    int i = 0, j = 0;
	    while (e.hasMoreElements()) {
		proxies.remove(e.nextElement());
		i++;
	    }
	    e = idleClients.elements();
	    while (e.hasMoreElements()) {
		try {
		    ((ClientSession) e.nextElement()).close();
		    j++;
		} catch (IOException ioException) {
		    warn("I/O error while trying to close idle client: " + ioException.toString());
		}

	    }
	    if (printStatus && (i > 0 || j > 0))
		status("removed " + i + " proxies and " + j + " idle clients");
	} else {
	    idleCount++;
	    if (printStatus && idleCount > 5) {
		status("no proxies alive");
		idleCount = 0;
	    }
	}

    }

    /**
     * This is the garbage collector thread (which also serves the
     * optional status messages).  Every GCInterval (configuration
     * value) seconds, the garbage collector is run.  The garbage
     * collector is responsible for dereferencing any ClientProxy
     * object without clients, and closing connections which have been
     * idle for too long (configuration option "ClientIdleTimeout").
     *
     * Also, the GC traverses the thread pool to see if there are any
     * dead threads in the pool; in that case, it replaces them with
     * new pooled threads.
     */
    public void run() {
	while (runGC) {
	    try {
		Thread.sleep(config.getIntProperty("GCInterval") * 1000);
	    } catch (InterruptedException ex) { // maybe fatal()?
		warn("GC thread interrupted (" + ex.getMessage() + ")");
		runGC = false; // go to bed.
		continue; // skip last gc run
	    }
	    gc();
	}
    }

    /**
     * Returns the uptime of the server in milliseconds
     */
    public long getUptime() {
	return System.currentTimeMillis() - bootTime;
    }

    /**
     *  Used to debug client communication. Prints out client information
     *  (ClientConnection.toString()) along with a timestamp and the supplied String to stdout.
     */
    public static final synchronized void debug(Object o, String s) {
	debug(o.toString() + " " + s);
    }

    /**
     * Generic debug outputter.
     */
    public static final synchronized void debug(String s) {
	if (debug) println(System.currentTimeMillis() + " [debug] " + s);
    }

    /**
     * Output a status message to the standard log (in current implementation stdout)
     */
    public static synchronized void status(String s) {
	println(System.currentTimeMillis() + " [status] " + s);
    }

    /**
     * Output a warning message to stderr.
     */
    public static synchronized void warn(String s) {
	debug("[warning] " + s);
    }
    
    private static JFrame createFrame() {
	JFrame frame = new JFrame();
	frame.setSize(800,600);
	frame.setVisible(true);
	return frame;	
    }
    
    
    
    
    private static void initGUI() {
	JFrame frame = createFrame();
	console = new Console(25, 80);
	console.setConsoleThingies("");
	frame.getContentPane().add(new JScrollPane(console));
	frame.setTitle("Bricole XML Session Server");
	frame.setVisible(true);
	frame.addWindowListener(new WindowAdapter() {	    
	    public void windowClosing(WindowEvent e) {
		status("Attempting shutdown...");
		new Thread() {
		    public void run() {
			System.setOut(null);
			System.setErr(null);
			System.exit(0);
		    }
		}.start();
	    }
	});
	frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
	frame.requestFocus();

	console.setFont(new Font("monospaced", Font.PLAIN, 14));
	System.setOut(new PrintStream(new LogOutputStream(console)));
	
	/*
	console.addConsoleListener(this);
	console.addComponentListener(new ComponentAdapter() {
		public void componentResized(ComponentEvent e) {
		    int rows = console.getRows();
		    Debug.println("Changed console rows to " + rows);
		    linesPerScreen = rows; // has no effect, never changes 
		}
	    });
        */

	System.setErr(new PrintStream(new LogOutputStream(console)));

	
    }
    
    
    static boolean gui = false;
    static Console console = null;
    protected synchronized static  void println(String s) {
	if (gui && console == null) initGUI();
	System.out.println(s);
	
    }

    /**
     * This method should be called when an unrecoverable error has been encountered
     * and execution cannot (or should not) continue. An error message will me printed
     * on stderr and the JVM will exit.
     */
    public static synchronized void fatal(String s) {
	System.err.println(System.currentTimeMillis() + " fatal: " + s);
	System.exit(-1);
    }

    public static void main(String[] argc) {
	bootTime = System.currentTimeMillis();
	status(PRODUCT_NAME + " " + getVersionString() + " starting up...");
	Server s = null;
	gui = argc.length == 0;
	if (argc.length < 1) {
	    warn("No argument supplied - using default configuration.");
	    s = new Server();
	} else {
	    s = new Server(argc[0]);
	}
	debug("using gui? " + gui);
	debug("<-- main()");
    }

    Configuration config = null;
    /**
     * Reads the configuration (which in turn may load and register any modules 
     * specified in the configuration).
     * Launches all pool threads and a garbage collecting thread.
     */
    public Server(String configfile) {
	this.configFile = configfile;
	initServer();
	setupHooks();
    }
    
    public Server() {
	configFile = null;
	initServer();
	setupHooks();
    }
    
    public Server(InputStream is) {
	this.configFile = null;
	initServer();
	setupHooks();
    }
    
    private void setupHooks() {
	Runtime.getRuntime().addShutdownHook(new Thread() {
		public void run() {
		    if (!shutdown) {
			shutdown(false);
		    }
		}
	    });
	
    }
    
    private void initServer() {
	if (configFile == null) {
	    
	    try {
		InputStream is = getClass().getClassLoader().
			getResourceAsStream("se/bricole/xss/default-config.xml");
		initServer(is);
		is.close();
	    } catch (IOException ex1) {
		fatal("I/O error: " + ex1.getMessage());
	    }	    
	} else {
	    initServer(configFile);
	}
	
    }
    
    private void initServer(String configFileName) {
	File f = new File(configFileName);
	try {
	    FileInputStream is = new FileInputStream(f);
	    initServer(is);
	    is.close();
	} catch (IOException ex1) {
	    fatal("I/O error: " + ex1.getMessage());
	}
    }

    private void initServer(InputStream is) throws IOException {
	config = new Configuration(is, this, new File(File.separatorChar + "XSS"));
	srv = null;
	try {
	    srv = new ServerSocket(config.getIntProperty("ListenPort"), 1000,
				   InetAddress.getByName(config.getStringProperty("ListenAddress")));
	} catch (IOException ex) {
	    fatal("Error binding server socket: " + ex.getMessage());
	}

	growableThreadPool = config.getIntProperty("GrowableThreadPool") == 1 ? true : false;
                
	maxProxies = config.getIntProperty("ProxiesPerServer");
	usersPerProxy = config.getIntProperty("UsersPerProxy");

	threadPool = new ClientSession[config.getIntProperty("InitialThreadPool")];

	// code duplication.. the gc run() also does some of this
	for (int i = 0; i < threadPool.length; i++) {
	    threadPool[i] = new ClientSession(srv, this);
	    threadPool[i].addSessionEventListener(this);
	    threadPool[i].setPoolSlot(i);
	    threadPool[i].start();
	}

	Thread gc = new Thread(this);
	gc.setPriority(Thread.MIN_PRIORITY);
	gc.setName("XSS-GarbageCollector");
	gc.setDaemon(true);
	gc.start();
	gcThread = gc;

	Server.status("all threads launched (" + threadPool.length + " threads in pool)"); 
    }

    public void restart() {
	Server.status("Server restart initiated.");
	Server.status("Nuking client threads...");
	killPool();
	Server.status("Re-initializing...");
	initServer();
	Server.status("Server restart complete.");
    }


    /**
     * Shuts down the server, and optionally the entire JVM.
     *
     */
    public void shutdown(boolean quitJVM) {
	shutdown = true;
	Server.status("Shutting down server!");
	runGC = false;
	gcThread.interrupt();
	killPool();
	Server.status("Shutdown complete (some threads may still be finishing).");
	if (quitJVM) System.exit(0);
    }

    private void killPool() {
	for (int i=0; i < threadPool.length; i++) {
	    if (threadPool[i] != null) threadPool[i].setKeepAlive(false);
	}
	try {
	    if (srv != null) srv.close();
	} catch (IOException ex1) {

	}
	for (int i=0; i < threadPool.length; i++) {
	    if (threadPool[i] != null && threadPool[i].isAlive()) {
 		threadPool[i].interrupt();
		Server.status("Waiting for thread " + i);
		try {
		    threadPool[i].finish();
		    threadPool[i].join();
		} catch (InterruptedException ex1) {
		}
	    }
	}
    }

    protected synchronized ClientProxy findClientProxy()
    throws NoProxyAvailableException {
	ClientProxy p = null;
	Enumeration e = proxies.elements();
	while (e.hasMoreElements()) {
	    p = (ClientProxy) e.nextElement();
	    if (p.getClientCount() < usersPerProxy) {
		return p;
	    }
	}

	if (proxies.size() < maxProxies) {
	    p = new ClientProxy(proxyCounter++, this, config, this);
	    proxies.add(p);
	    status("creating new ClientProxy " + p.toString());
	    return p;
	} else {
	    warn("Maximum number of proxies (" + maxProxies + ")!");
	    throw 
		new NoProxyAvailableException("Proxy count has reached limit (" + maxProxies + ")"); 
	}

    }

    /**
     * Broadcast message "msg" originating from "source" to _all_ clients.
     * If "override" is true, all clients will receive the message, regardless of the connections'
     * "receiveBroadcasts" property. If the server is heavily loaded, use this method with care.
     */
    public synchronized void broadcast(ClientSession source, 
				       String msg, 
				       boolean override) {
	Enumeration e = proxies.elements();
	while (e.hasMoreElements()) {
	    ((ClientProxy) e.nextElement()).broadcast(source, msg, override);
	}
    }
    public void clientStart(ClientSession c) {
	clientCount++;
	status(
	       "new client "
	       + c.toString()
	       + " from "
	       + c.getInetAddress().toString()
	       + " ("
	       + c.getProxy().getClientCount()
	       + " clients in "
	       + c.getProxy().toString()
	       + ", " 
	       + clientCount
	       + " total).");

	if (!expanding && clientCount >= threadPool.length) {
	    if (growableThreadPool && clientCount == threadPool.length) {
		Thread t = new Thread(new Runnable() {
			public void run() {
			    expandPool();
			}
		    });
		t.setName("ThreadPoolManager");
		t.start();
	    } else {
		Server.debug("thread pool is full, launching one-shot thread");
		final ClientSession t = new ClientSession(srv, this);
		t.setKeepAlive(false);
		t.addSessionEventListener(this);
		t.addSessionEventListener(new SessionEventListener() {
			public void clientStart(ClientSession c) {}
                            
			public void clientStop(ClientSession s) {
			    looseThreads.remove(t);
			}
		    });
		looseThreads.add(t);
		t.start();                        
	    }
	}
		 
    }
    public void clientStop(ClientSession c) {
	clientCount--;
	ClientProxy p = c.getProxy();
	status("lost client "
	       + c.toString()
	       + " ("
	       + (p != null ? ""+p.getClientCount() : "?")
	       + " clients in "
	       + (p != null ? p.toString() : "?")
	       + ")."); 
    }


    boolean expanding = false;
    Object threadPoolLock = new Object();
    public void expandPool() {
	synchronized (threadPoolLock) {
	    if (!expanding) {
	        expanding = true;
		ClientSession[] newPool = new ClientSession[threadPool.length + 5];
		System.arraycopy(threadPool, 0, newPool, 0, threadPool.length);
		for (int i = threadPool.length; i < newPool.length; i++) {
		    newPool[i] = new ClientSession(srv, this);
		    newPool[i].setPoolSlot(i);
		    newPool[i].setDaemon(false);
		    newPool[i].start();
		}
		threadPool = newPool;
		status("New pool allocated with " + threadPool.length + " threads");
		expanding = false;
	    }
	}
    }
}

