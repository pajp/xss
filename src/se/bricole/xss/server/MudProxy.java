package se.bricole.xss.server;

import java.net.*;
import java.io.*;


class MudProxy {
    public final static String vcId = "$Id: MudProxy.java,v 1.3 2002/09/12 00:54:52 pipeman Exp $";

    public final static String GREETING_NAME = "By what name do you wish to be known?";
    public final static String GREETING_PASSWORD = "Password:";
    
    Socket mudSocket;
    ClientSession client;

    public MudProxy(ClientSession c)
    throws IOException {
	Server.debug(c, "[MUD] Connecting to server...");
	mudSocket = new Socket("localhost", 4000);
	Server.debug(c, "[MUD] Connected");
	client = c;
    }
    
    public void setProperties(java.util.Properties p) {}

    public void login(String user, String password) {
	try {
	    InputStream input = mudSocket.getInputStream();
	    OutputStream output = mudSocket.getOutputStream();
	    int b = -2;
	    boolean keepAlive = true;

	    while (keepAlive && b != -1) {
		StringBuffer buff = new StringBuffer();
		b = input.read();
		while (b != -1 && b != 0 && b != '\n') {
		    buff.append((char) b);
		    b = input.read();
		    
		    if (buff.length() >= GREETING_NAME.length()) {
			if (buff.toString().equals(GREETING_NAME)) {
			    output.write((user+"\n").getBytes());
			    Server.debug(client, "[MUD] Username sent");
			}
		    }
		    if (buff.length() >= GREETING_PASSWORD.length()) {
			if (buff.toString().equals(GREETING_PASSWORD)) {
			    output.write((password+"\n").getBytes());
			    Server.debug(client, "[MUD] Password sent");
			}
		    }
		    
		}
		Server.debug(client, "[MUD] [output] " + buff.toString());
	    }
	} catch (IOException ex) {
	    System.err.println("[MUD] I/O error: " + ex.getMessage());
	}
    }



}
