package se.bricole.xss.server;

import java.net.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Set;
import java.util.HashSet;

import org.w3c.dom.Document;

/**
 * <p>
 * Each ClientConnection is associated with exactly one client, and is therefore the primary
 * interface for all client I/O and communication.
 * </p>
 * <p>The fact that this is a subclass of <code>Thread</code> is an
 * implementation detail side effect and should change in the future,
 * so developers should rely on the Thread API being there. Bad OO,
 * but I was young and innocent.</p>
 */
public class ClientSession extends Thread {

    public final static String vcId = "$Id: ClientSession.java,v 1.4 2002/09/12 00:00:34 pipeman Exp $";

    static final boolean broadcastUnknownXML = false;

    private boolean receiveBroadcasts = false;

    Socket socket = null;
    ServerSocket serverSocket = null;
    Server server = null;
    ClientProxy proxy = null;

    private long lastSend = System.currentTimeMillis();

    int proxyId = -1, poolSlot = -1;

    boolean keepAlive = true;
    boolean active = false;

    OutputStream output;
    InputStream input;

    CommandParser parser = null;

    Properties clientProperties;
    Hashtable clientObjects = new Hashtable();

    Set authenticatedDomains = new HashSet();
    boolean isAuthenticated = false;

    public boolean isAuthenticated() {
	return isAuthenticated;
    }

    public boolean isAuthenticatedTo(String domain) {
	synchronized (authenticatedDomains) {
	    return authenticatedDomains.contains(domain);
	}
    }

    protected void authenticatedTo(String domain) {
	synchronized (authenticatedDomains) {
	    authenticatedDomains.add(domain);
	    checkIsAuthenticated();
	}
    }

    protected void deAuthenticatedTo(String domain) {
	synchronized (authenticatedDomains) {
	    authenticatedDomains.remove(domain);
	    checkIsAuthenticated();
	}
    }

    private void checkIsAuthenticated() {
	isAuthenticated = authenticatedDomains.size() > 0;
    }

    protected int getPoolSlot() {
	return poolSlot;
    }

    protected ClientSession(ServerSocket serverSocket, Server server) {
	this.serverSocket = serverSocket;
	this.server = server;
    }
	
    /**
     * Returns the boolean value of the "receiveBroadcasts" property.
     * If this property is set to "false", this client will not
     * receive broadcasts sent with
     * ClientProxy.broadcast(ClientConnection, String).
     */
    public boolean getReceiveBroadcasts() {
	return receiveBroadcasts;
    }

    /**
     * Sets the boolean value of the "receiveBroadcasts" property for
     * this client.  If this property is set to "false", this client
     * will not receive broadcasts sent with
     * ClientProxy.broadcast(ClientConnection, String).
     */
    public void setReceiveBroadcasts(boolean b) {
	receiveBroadcasts = b;
    }

    /**
     * Similar to getSharedObject()/setSharedObject() in the
     * ClientProxy class, arbitrary object references can be stored in
     * a ClientConnection class, with the difference that the only
     * valid key is a String object.
     */
    public void putObject(String key, Object o) {
	clientObjects.put(key, o);
    }

    /**
     * Similar to getSharedObject()/setSharedObject() in the
     * ClientProxy class, arbitrary object references can be stored in
     * a ClientConnection class, with the difference that the only
     * valid key is a String object.
     */
    public Object getObject(String key) {
	return clientObjects.get(key);
    }

    public Object removeObject(String key) {
	return clientObjects.remove(key);
    }

    /**
     * Along with the object reference storage of
     * getObject()/setObject(), The ClientConnection class provides
     * the methods getProperty() and setProperty() to let a user store
     * String objects with String keys (á la java.util.Properties).
     */
    public void setProperty(String key, String value) {
	clientProperties.setProperty(key, value);
    }

    /**
     *
     * Returns the ClientProxy object to which this ClientConnection
     * is associated.
     *
     */
    public ClientProxy getProxy() {
	return proxy;
    }

    /**
     *
     * Along with the object reference storage of
     * getObject()/setObject(), The ClientConnection class provides
     * the methods getProperty() and setProperty() to let a user store
     * String objects with String keys (á la java.util.Properties).
     *
     */
    public String getProperty(String key) {
	return clientProperties.getProperty(key);
    }

    /**
     * Returns an integer unique for this server instance representing
     * this ClientConnection.
     */
    public int getId() {
	return proxyId;
    }

    private void setId(int id) {
	proxyId = id;
    }

    public synchronized void send(Document doc) throws IOException {
	send(XMLUtil.documentToString(doc));
    }

    /**
     *
     * Sends a NULL-terminated bunch of octets to the client. The
     * octets sent are acquired from the String object using
     * String.getBytes(). There is room for improvement here.
     *
     */
    public synchronized void send(String s) throws IOException {
	if (socket == null || input == null || output == null) {
	    throw new IOException("Some I/O object is null");
	}

	output.write((s + "\000").getBytes());
	lastSend = System.currentTimeMillis();
    }

    /**
     * Closes the TCP socket associated with this ClientConnection
     */
    public void close() throws IOException {
	socket.close();
    }

    /**
     * Generally used when something goes really bad. Closes socket.
     */
    protected void carefulCleanup() {
	if (proxy != null) {
	    proxy.remove(this);
	}

	if (socket != null) {
	    try {
		socket.close();
	    } catch (IOException ioe) {
		Server.debug(this, "carefulCleanup(): I/O Exception: " + ioe.getMessage());
	    }
	}
	proxyId = -1;
	proxy = null;
		

    }

    /**
     * Returns the peer Internet address of this client's socket.
     */
    public InetAddress getInetAddress() {
	return socket.getInetAddress();
    }

    /**
     * Returns an identifying string.
     */
    public String toString() {
	return "[CS id:"
	    + proxyId
	    + " proxy:"
	    + (proxy != null ? proxy.getId() : -1)
	    + " pooled:"
	    + poolSlot
	    + "]"; 
    }

    /**
     * Sets whether this is a keep-alive thread or not.
     * If false, the thread will terminate as soon as it has served it's first
     * (or next, if set during execution) client.
     */
    public void setKeepAlive(boolean keepAlive) {
	this.keepAlive = keepAlive;
    }
        
    /**
     * This is the main loop, which reads NULL-terminated (\0) strings and calls the XML parser (which in turn
     * may call a matching XML module).
     */
    public void run() {
	do {
	    try {
		proxyId = -1;
		proxy = null;
		delayedFinishInProgress = false;
				
		Server.debug(this, "waiting for connection");
		setName("IdleClientThread-" + poolSlot);
		active = false;
		try {
		    socket = serverSocket.accept();
		} catch (SocketException se) {
		    Server.debug(this, "SocketException: " + se.getMessage() + " -- quitting");
		    keepAlive = false;
		    continue;
		}
		active = true;
		setName("ActiveClientThread-" + poolSlot);

		setup(socket, server.findClientProxy());
		while(blockingSocketRead(socket));

	    } catch (IOException ioe) {
		Server.debug(this, 
			     "I/O error: " + ioe.getClass().toString() + ": " + ioe.getMessage()); 
	    } catch (NoProxyAvailableException nae) {
		Server.warn("refused connection; proxy count limit reached");
	    }
                        
	    /**
	     * cleanup.
	     */
                        
	    if (proxy != null) proxy.remove(this);
                        
	    Enumeration e = sessionEventListeners.elements();
	    while (e.hasMoreElements()) {
 		SessionEventListener l = (SessionEventListener) e.nextElement();
		Server.debug("calling " + l + ".clientStop(" + this + ")");
		l.clientStop(this);
	    }

	} while (keepAlive);
	active = false;
    }

    public boolean isActive() {
	return active;
    }



    /**
     * Dereferences and closes this session.
     */
    public void finish() {
	keepAlive = false;

	if (proxy != null)
	    proxy.remove(this);
	if (socket != null) {
	    try {
		socket.close();
	    } catch (IOException e) {

	    }
	    socket = null;
	}

// 	if (poolSlot == -1) { // huh?
// 	    keepAlive = false;
// 	}

    }

    public void resetProxy() {
	proxy = null;
	proxyId = -1;
    }

    /**
     *
     */
    public boolean socketIsNull() {
	return socket == null;
    }

    /**
     * Returns the number of milliseconds elapsed since this client sent any data.
     */
    public long getIdleTimeMillis() {
	return System.currentTimeMillis() - lastSend;
    }
    protected void setup(Socket s, ClientProxy p) throws IOException {
	this.socket = s;
	this.proxy = p;

	setId(Server.getGUID());
	proxy.add(this);
	output = socket.getOutputStream();
	input = socket.getInputStream();

	clientProperties = new Properties();

	try {
	    parser = (CommandParser) new XMLCommandParser(proxy, this);
	} catch (ParserException pe) {
	    Server.warn(this.toString() + " failed to initialize parser");
	}
    }

    Vector sessionEventListeners = new Vector(1);	

    /**
     * Adds a ConnectionEventReceiver to the list of listeners which
     * will receive a notification when something happens to this
     * object.
     */
    public void addSessionEventListener(SessionEventListener c) {
	sessionEventListeners.add(c);
    }

    boolean delayedFinishInProgress = false;
    /**
     * Creation date: (2001-06-11 05:35:18)
     */
    public void delayedFinish() {
	delayedFinishInProgress = true;
    }
    public boolean isInDelayedFinish() {
	return delayedFinishInProgress;
    }
    private boolean blockingSocketRead(Socket s)
	throws IOException {	    
	StringBuffer buff = new StringBuffer();
	int b = -2;
	b = input.read();
	while (b != -1 && b != 0) {
	    buff.append((char) b);
	    b = input.read();
	}
	if (b == -1) {
	    //keepAlive = false;
	    Server.status(this.toString() + ": connection closed by foreign host");
	    return false;
	}
	boolean broadcast = true;

	Server.debug(this, "Got XML: " + buff.toString());
	if (parser != null) {
	    try {
		broadcast = parser.parse(buff);
	    } catch (ParserException pe) {
		Server.warn(this.toString() + ": parse error: " + pe.getMessage());
	    } catch (NullPointerException npe) {
		// TODO: still don't know why NPE occurs when client issues "quit"
		Server.warn(this.toString() + ": Null Pointer Exception in parser");
		npe.printStackTrace();
		finish();
	    }
	}
	if (broadcast && broadcastUnknownXML) {
	    proxy.broadcast(this, buff.toString());
	}
	// might cause bad stats in high load
	if (proxy != null)
	    proxy.setCommandCount(proxy.getCommandCount() + 1);
		
		
	return true;

    }
	
    /**
     * Creation date: (2001-06-11 19:46:31)
     * @author Rasmus Sten
     * @param id int
     */
    public void setPoolSlot(int id) {
	poolSlot = id;	
    }
	
    protected void setServerSocket(ServerSocket s) {
	this.serverSocket = s;
    }
    private long lastClient = System.currentTimeMillis();
}
