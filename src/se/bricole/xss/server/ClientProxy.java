package se.bricole.xss.server;

import java.io.*;
import java.util.*;

/**
 * 
 * <p>The ClientProxy object acts as the gateway between clients.
 * The only way to get a reference to another client
 * is through the ClientProxy object.</p>
 *
 * <p>The server may keep a number of ClientProxy objects, each of
 * which are handling a number of clients. A client associated with
 * certain ClientProxy can not access clients within other ClientProxy
 * objects. This is a way to isolate groups of clients in order to
 * limit both client and server bandwidth requirements (especially in
 * case of many broadcasts, such has chat rooms).</p>
 *
 */
public class ClientProxy {

    public final static String vcId = "$Id: ClientProxy.java,v 1.3 2002/09/12 00:00:34 pipeman Exp $";


    Configuration configuration;
    Server server;

    SessionEventListener listener;
    Vector clients = new Vector();
    Hashtable sharedData = new Hashtable();

    int id;
    int clientCount = 0;
    int idCount = 0;
    int broadcastCount = 0;

    int commandCount = 0;

    /**
     * <p>The methods addSharedObject() and getSharedObject() provides a way of sharing objects among clients
     * in a ClientProxy. The functionality is basically that of a Hashtable or HashMap.</p>
     *
     * @param	key	the hash key
     * @param	value	the hash value
     */
    public void addSharedObject(Object key, Object value) {
	sharedData.put(key, value);
    }

    /**
     * <p>The methods addSharedObject() and getSharedObject() provides a way of sharing objects among clients
     * in a ClientProxy. The functionality is basically that of a Hashtable or HashMap.</p>
     *
     * @param	key	the hash key
     * @returns	the object associated with <i>key</i>, or null if none exists.
     */
    public Object getSharedObject(Object key) {
	return sharedData.get(key);
    }



    /**
     * Returns a unique integer identifier for this ClientProxy object.
     */
    public int getId() {
	return id;
    }

    /**
     * Returns a string representation of the form ClientProxy#<i>n</i>, where <i>i</i> equals the return value
     * of getId().
     */
    public String toString() {
	return "ClientProxy#" + getId();
    }

    /**
     * Returns the Configuration objects used upon instantiation of this ClientProxy.
     */
    public Configuration getConfiguration() {
	return configuration;
    }

    /**
     * Sets the commandCount property.
     */
    protected void setCommandCount(int c) {
	commandCount = c;
    }

    /**
     * Returns the number of commands received from this ClientProxy's clients.
     */
    public int getCommandCount() {
	return commandCount;
    }

    /**
     * Returns the number of broadcasts that has been done in this ClientProxy.
     */
    public int getBroadcastCount() {
	return broadcastCount;
    }

    /**
     * Returns the number of clients currently associated with this ClientProxy.
     */
    public int getClientCount() {
	return clientCount;
    }

    /**
     * Returns an Enumeration containing all ClientConnection objects associated with this ClientProxy.
     */
    public Iterator getClients() {
	List _clientList = new LinkedList();
	_clientList.addAll(clients);
	return _clientList.iterator();
    }

    /**
     * Does an integrity check of this ClientProxy.
     */
    protected void checkIntegrity() {
	if (clients == null)
	    Server.fatal("ClientProxy integrity check failed, vector is null");
	if (clients.size() != clientCount)
	    Server.debug(this,
			 "ClientProxy integrity check failed, vector size ("
			 + clients.size()
			 + ") != count ("
			 + clientCount
			 + ")"); 
    }

    protected ClientProxy(
			  int id, 
			  SessionEventListener listener, 
			  Configuration config, 
			  Server server) {
	this.id = id;
	this.listener = listener;
	this.configuration = config;
	this.server = server;
    }
    
    protected void add(ClientSession c) {
	checkIntegrity();
	clients.add(c);
	clientCount++;
	listener.clientStart(c);
    }	
        
    /**
     * Sends a broadcast message <i>s</i> to all clients that belongs to this ClientProxy whose
     * receiveBroadcasts property is set to <i>true</i>.
     *
     * @param	source	the ClientConnection object of the sender
     * @param	s	the string to broadcast
     */
    public void broadcast(ClientSession source, String s) {
	broadcast(source, s, false);
    }	
        
    /**
     * <p>Broadcasts a message to all clients that belongs to this ClientProxy. The <i>override</i>
     * parameter whether to honour the the <i>receiveBroadcasts</i> property of clients' ClientConnection 
     * object</p>
     *
     * @param	source	the ClientConnection object which the message is originating from
     * @param	s	the string to broadcast
     * @param	override	if <i>true</i>, the <i>receiveBroadcasts</i> property of the clients' is not honoured.
     */
    public void broadcast(ClientSession source, String s, boolean override) {
	checkIntegrity();
	Enumeration e = clients.elements();

	int count = 0;
	while (e.hasMoreElements()) {
	    ClientSession client = (ClientSession) e.nextElement();

	    if (client == source || (!override && !client.getReceiveBroadcasts())) {
		continue;
	    }

	    try {
		client.send(s);
	    } catch (IOException ex) {
		Server.warn("Error sending to " + client.toString());
	    }
	    count++;
	}
	broadcastCount++;
    }
        
    /**
     * Request that the specified ClientConnection is deferenced.
     */
    protected void remove(ClientSession c) {
	if (clients.contains(c)) {
	    checkIntegrity();
	    clients.remove(c);
	    clientCount--;
	}
	//listener.clientStop(c);
    }
        
    /**
     * Broadcast message "s" originating from "source" to _all_ clients.
     * If "override" is true, all clients will receive the message, regardless of the connections'
     * "receiveBroadcasts" property. If the server is heavily loaded, use this method with care.
     */
    public void serverBroadcast(ClientSession source, String s, boolean override) {
	server.broadcast(source, s, override);
    }
}