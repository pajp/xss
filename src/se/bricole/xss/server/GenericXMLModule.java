package se.bricole.xss.server;

import java.io.*;
import java.util.Properties;
import java.util.Random;
import javax.xml.parsers.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

import org.apache.xerces.dom.DocumentImpl;



/**
 * This module implements some basic stateless XML tags, such as &lt;ping/&gt;,
 * &lt;get-status/&gt; and &lt;quit/&gt; and most importantly &lt;auth/&gt;.
 */
class GenericXMLModule implements XMLTagHandler {

    public final static String vcId = "$Id: GenericXMLModule.java,v 1.4 2002/09/12 00:54:52 pipeman Exp $";

    public static final String[] tagNames = { "auth", "ping", "get-status", "quit" };
    
    ClientProxy proxy;
    ClientSession client;
    Properties properties = new Properties();

    public String[] getTagNames() {
	return tagNames;
    }


    public GenericXMLModule() {
	this.proxy = proxy;
	this.client = client;
    }

    Random random = new Random();
    int randomInteger() {
	return random.nextInt(65536);
    }

    public boolean xmlTag(ClientSession client, ClientProxy proxy, Element e)
	throws ParserException, IOException {
	
	if (e.getTagName().equals("auth")) {
	    Properties attr = new Properties();
	    String domain = e.getAttribute("domain");
	    if (e.hasAttribute("sid")) {
		attr.setProperty("sid", e.getAttribute("sid"));
	    }
	    if (domain == null || domain.equals("")) {
		attr.setProperty("message", "no \"domain\" attribute");
		client.send(XMLUtil.simpleTag("auth-error", attr));
		return true;
	    }
	    if (e.hasAttribute("init-challenge")) {
		AuthHandler module = proxy.getConfiguration().getAuthenticatorByDomain(domain);
		if (!(module instanceof AuthHandlerPassive)) {
		    attr.setProperty("message", "authenticator does not support challenge-response");
		    client.send(XMLUtil.simpleTag("auth-error", attr));
		    return true;
		}

		Integer chal = new Integer(randomInteger());
		client.putObject("auth.challenge." + domain, chal.toString());
		client.putObject("auth.challenge." + domain + ".username",
				 e.getAttribute("username"));
		attr.setProperty("challenge", chal.toString());
		client.send(XMLUtil.simpleTag("auth-challenge", attr));
		return true;
	    }

	    if (e.hasAttribute("challenge-response")) {
		String clientMD5 = e.getAttribute("challenge-response");
		Object _challenge = client.getObject("auth.challenge." + domain);
		//Server.debug("challenge obj: " + _challenge.getClass().getName());
		String challenge = (String) _challenge;
		if (challenge == null) {
		    attr.setProperty("message", "No challenge has been initiated");
		    client.send(XMLUtil.simpleTag("auth-error", attr));
		    return true;
		}

		byte[] clientMD5bytes = hexStringToBytes(clientMD5);

		AuthHandlerPassive module = (AuthHandlerPassive)
		    proxy.getConfiguration().getAuthenticatorByDomain(domain);
		String username = (String) client.getObject("auth.challenge." +
							    domain + ".username");
		char[] cpassword = module.getPassword(client, proxy, domain, username);
		if (cpassword == null) {
		    attr.setProperty("message", "access denied by authenticator");
		    attr.setProperty("domain", domain);
		    client.removeObject("auth.challenge."+domain);
		    client.removeObject("auth.challenge."+domain+".username");
		    client.send(XMLUtil.simpleTag("auth-error", attr));
		    return true;
		}
		byte[] chalBytes = challenge.getBytes("iso-8859-1");
		byte[] string = new byte[cpassword.length + chalBytes.length];
		for (int i=0; i < cpassword.length; i++) {
		    string[i] = (byte) cpassword[i];
		}
		for (int i=cpassword.length; i < string.length; i++) {
		    string[i] = chalBytes[i-cpassword.length];
		}

		try {

		    MessageDigest md = MessageDigest.getInstance("MD5");
		    byte[] serverBytes = md.digest(string);
		    
		    StringBuffer serverDataBuf =
			new StringBuffer();
		    for (int i=0; i < serverBytes.length; i++) {
			String hex = Integer.toHexString(serverBytes[i]+128);
			serverDataBuf.append(hex.length() == 1 ? "0" + hex : hex);
		    }
		    String serverData = serverDataBuf.toString();

		    boolean success = MessageDigest.isEqual(clientMD5bytes,
							    serverBytes);
		    if (success) {
			client.authenticatedTo(domain);
			client.setProperty("auth." + domain + ".username", username);
			attr.setProperty("message", "access granted by authenticator");
			attr.setProperty("domain", domain);
			client.send(XMLUtil.simpleTag("auth-ok", attr));
		    } else {
			Server.debug("Authentication failed: got " + clientMD5 + 
				     ", expected: " + serverData);
				     
			attr.setProperty("message", "access denied by authenticator");
			attr.setProperty("domain", domain);
			client.send(XMLUtil.simpleTag("auth-error", attr));
		    }
		} catch (NoSuchAlgorithmException ex1) {
		    throw new RuntimeException("Missing MD5", ex1);
		} finally {
		    client.removeObject("auth.challenge."+domain);
		    client.removeObject("auth.challenge."+domain+".username");
		}
		return true;
	    }
	    

	    if (!e.hasAttribute("username") || !e.hasAttribute("password")) {
		attr.setProperty("message", "missing username och password attribute");
		client.send(XMLUtil.simpleTag("auth-error", attr));
		return true;
	    }

	    AuthHandler module = proxy.getConfiguration().getAuthenticatorByDomain(domain);
	    if (module == null) {
		attr.setProperty("message", "invalid value in \"domain\" attribute");
		client.send(XMLUtil.simpleTag("auth-error", attr));
		return true;
	    }
	    
	    if (module.authenticate(client, proxy, domain, e.getAttribute("username"),
				    e.getAttribute("password").toCharArray())) {
		client.authenticatedTo(domain);
		attr.setProperty("message", "access granted by authenticator");
		attr.setProperty("domain", domain);
		client.send(XMLUtil.simpleTag("auth-ok", attr));
	    } else {
		attr.setProperty("message", "access denied by authenticator");
		attr.setProperty("domain", domain);
		client.send(XMLUtil.simpleTag("auth-error", attr));
	    }
	    return true;
		
	}
	
	if (e.getTagName().equals("ping")) {
	    Properties attr = new Properties();
	    if (!e.getAttribute("sid").equals(""))
		attr.setProperty("sid", e.getAttribute("sid"));
	    client.send(XMLUtil.simpleTag("pong", attr));
	    return true;
	}
	if (e.getTagName().equals("quit")) {
	    Properties attr = new Properties();
	    if (!e.getAttribute("sid").equals(""))
		attr.setProperty("sid", e.getAttribute("sid"));

	    attr.setProperty("tag", "quit");
	    attr.setProperty("message", "see ya");
	    client.send(XMLUtil.simpleTag("ack", attr));
	    client.delayedFinish();
	    return true;
	}
	if (e.getTagName().equals("shutdown")) {
	    Properties attr = new Properties();
	    return true;
	}
	if (e.getTagName().equals("get-status")) {
	    try {
		Document replyDoc = new DocumentImpl();

		Element statroot = replyDoc.createElement("status");

		statroot.setAttribute("clientCount", "" + proxy.getClientCount());
		statroot.setAttribute("yourIp", client.getInetAddress().getHostAddress());
		statroot.setAttribute("yourId", "" + client.getId());
		statroot.setAttribute("yourProxy", "" + proxy.getId());
		statroot.setAttribute("serverVersion", Server.getVersionString());
		statroot.setAttribute("uptime", "" + proxy.getConfiguration().getUptime());
		replyDoc.appendChild(statroot);

		OutputFormat format = new OutputFormat(replyDoc);
		format.setOmitXMLDeclaration(true);

		StringWriter stringOut = new StringWriter();
		XMLSerializer serializer = new XMLSerializer(stringOut, format);

		serializer.asDOMSerializer();
		serializer.serialize(replyDoc.getDocumentElement());
		client.send(stringOut.toString());

		return true;
	    } catch (DOMException dex) {
		throw new ParserException("Reply generation: " + dex.getMessage());
	    }
	} else {
	    return false;
	}
    }

    // buggy, don't use
    private String bytesToHexString(byte[] bytes) {
	StringBuffer buf = new StringBuffer();
	for (int i=0; i < bytes.length; i++) {
	    String hex = Integer.toHexString((int) bytes[i]);
	    if (hex.length() == 1) hex = "0" + hex;
	    buf.append(hex);
	}
	return buf.toString();
    }

    // be aware that the bytes are signed
    private byte[] hexStringToBytes(String s) {
	byte[] foo = new byte[s.length()/2];
	for (int i=0, j=0; i < s.length(); i += 2, j++) {
	    String byteStr = s.substring(i, i+2);
	    foo[j] = (byte) Integer.parseInt(byteStr, 16);
	}
	return foo;
    }

    public void setProperties(Properties p) {
	properties = p;
    }

}
