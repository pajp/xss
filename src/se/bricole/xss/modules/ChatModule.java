package se.bricole.xss.modules;

import java.io.*;
import javax.xml.parsers.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Properties;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

import org.apache.xerces.dom.DocumentImpl;

import se.bricole.xss.server.*;
// we can put some basic module functionality, for example:
// send(), getTagNames(), private? getProxy(),
// private? getConnection ...

public class ChatModule
	implements XMLSessionTagHandler, ChatProxy, SessionEventListener {
	final static int TAG_REGISTER = 0;
	final static int TAG_PRIVMSG = 1;
	final static int TAG_PUBMSG = 2;
	final static int TAG_QUIT = 3;
	final static int TAG_WHO = 4;
	final static int TAG_WHOIS = 5;
	final static int TAG_ECHO = 6;
	final static int TAG_STORE = 7;
	final static int TAG_GET = 8;

	final static String SO_CHATPROXY = "chatproxy";

	private String[] tagNames = {"register", // 0
		"privmsg", // 1
		"pubmsg", // 2
		"quit", // 3
		"who", // 4
		"whois", // 5
		"echo", // 6
		"store", // 7
		"get" // 8
	};
	ClientProxy proxy;
	ClientSession client;
	ChatProxy chatProxy;

        Properties properties = new Properties();

	ChatUser user = null;
	HashMap sessionVariables = new HashMap();

	public String[] getTagNames() {
		return tagNames;
	}

	public void setTagNames(String[] names) {
		tagNames = names;
	}

	public ChatUser getUser() {
		return user;
	}

        public void setProperties(Properties p) {
	    properties = p;
        }

	public boolean xmlTag(Element e) throws IOException {
		String tags[] = getTagNames();
		String rootTagName = e.getTagName();

		boolean ack = true;
		int error = -1;
		String errorMessage = null;

		Properties attr;
		Properties ackAttr = new Properties();

		int tagnum = -1;
		for (int i = 0; i < tags.length; i++) {
			if (tags[i].equals(rootTagName))
				tagnum = i;
		}

		if ((tagnum != TAG_REGISTER && tagnum != TAG_QUIT) && user == null) {
			tagnum = -1;
			error = ChatException.MUSTREGISTER;
			errorMessage = "Please register first!";
		}
		String scope;
		switch (tagnum) {
			case TAG_GET :
				scope = e.getAttribute("scope");
				if (scope.equals(""))
					scope = "session"; // default scope

				if (scope.equals("session")) {
					String key = e.getAttribute("name");
					if (key.equals("")) {
						error = 0;
						errorMessage = "empty name attribute";
						break;
					}
					String value = get(key);
					ackAttr.setProperty("value", value);

				} else {
					error = 0;
					errorMessage = "unknown scope";
				}
				break;

			case TAG_STORE :
				scope = e.getAttribute("scope");
				if (scope.equals(""))
					scope = "session"; // default scope

				if (scope.equals("session")) {
					String key = e.getAttribute("name");
					String value = e.getAttribute("value");
					if (key.equals("") || value.equals("")) {
						error = 0;
						errorMessage = "empty name or value attribute";
						break;
					}
					store(key, value);
				} else {
					error = 0;
					errorMessage = "unknown scope";
				}
				break;
			case TAG_ECHO :

				Document _doc = (Document) e.getParentNode();
				e.setAttribute("source", user.getNickname());
				e.setAttribute("sourceid", "" + client.getId());
				proxy.broadcast(client, XMLUtil.documentToString(_doc));

				break;

			case TAG_WHOIS :
				ChatUser target = null;

				try {
					target = chatProxy.getUser(e.getAttribute("nickname"));
				} catch (ChatException ex) {
					error = ex.getError();
					errorMessage = ex.getMessage();
					if (error != ChatException.NOSUCHNICK)
						Server.debug(
							client, 
							"whois error=" + error + " exception=" + ex.getClass().toString()); 
				}

				if (target != null) {
					ClientSession targetClient = target.getClient();
					attr = new Properties();

					attr.setProperty("id", "" + targetClient.getId());
					attr.setProperty("nickname", target.getNickname());
					attr.setProperty("realname", target.getRealname());
					attr.setProperty("other", target.getOther());
					attr.setProperty("host", targetClient.getInetAddress().toString());
					attr.setProperty("proxy", "" + targetClient.getProxy().getId());
					client.send(XMLUtil.simpleTag("whois", attr));

				} else {
					if (error == ChatException.NOSUCHNICK) {
						errorMessage = "No such registered nickname.";
					}
				}
				break;

			case TAG_QUIT : // the GenericXML just closes and ack's, we clean up here
				ack = false;
				if (user != null) {
					try {
						chatProxy.unregisterUser(user);
					} catch (ChatException ce) {
						if (ce.getError() == ChatException.NOSUCHNICK)
							Server.warn(
								"Tried to unregister non-existant nickname " + user.getNickname() + "!"); 
						else
							Server.warn(
								"Unexcepted ChatException: error="
									+ ce.getError()
									+ ", message=\""
									+ ce.getMessage()
									+ "\""); 
					}
				}

				broadcastUserPart(client, user, "requested");
				break;
			case TAG_WHO :
				Enumeration users = chatProxy.getUsers();
				Document doc = new DocumentImpl();
				Element root = doc.createElement("who");

				while (users.hasMoreElements()) {
					ChatUser u = (ChatUser) users.nextElement();
					Element userElement = doc.createElement("user");
					userElement.setAttribute("nickname", u.getNickname());
					userElement.setAttribute("id", "" + u.getClient().getId());
					root.appendChild(userElement);
				}
				doc.appendChild(root);

				String reply = XMLUtil.documentToString(doc);
				client.send(reply);
				break;

			case TAG_PUBMSG :
				attr = new Properties();
				attr.setProperty("from", user.getNickname());
				attr.setProperty("message", e.getAttribute("message"));
				proxy.broadcast(client, XMLUtil.simpleTag("pubmsg", attr));

				break;

			case TAG_PRIVMSG :
				String to = e.getAttribute("to");
				String message = e.getAttribute("message");

				ChatUser rcpt = null;
				try {
					rcpt = chatProxy.getUser(to);
				} catch (ChatException ex) {
					error = ex.getError();
					if (error == ChatException.NOSUCHNICK)
						errorMessage = "User not found";
					else
						errorMessage = ex.getMessage();
				}

				if (error == -1) {
					attr = new Properties();
					attr.setProperty("from", user.getNickname());
					attr.setProperty("message", message);
					rcpt.getClient().send(XMLUtil.simpleTag("privmsg", attr));
				}
				break;

			case TAG_REGISTER :
				String nickname = e.getAttribute("nickname").trim();
				String realname = e.getAttribute("realname").trim();
				String other = e.getAttribute("other").trim();
				ChatUser newUser = null;

				try {
					if (nickname.equals("")) {
						throw new ChatException("Illegal nickname");
					}
					if (user != null) {
						throw new ChatException("Cannot register twice (reconnect to re-register)");
					}
					newUser = new ChatUser(nickname, realname, other, client);

					chatProxy.registerUser(newUser);
				} catch (ChatException ex) {
					error = ex.getError();
					if (error == ChatException.NICKINUSE) {
						errorMessage = "Nickname already in use";
						ackAttr.setProperty("nickname", newUser.getNickname());
					} else
						errorMessage = ex.getMessage();

				} finally {
					if (error == -1) {
						user = newUser;
						/** "ack" response **/
						ackAttr.setProperty("id", "" + client.getId());
						ackAttr.setProperty("nickname", user.getNickname());
						Server.status(
							client + " [Chat] registered nickname \"" + user.getNickname() + "\""); 

						client.setReceiveBroadcasts(true);
						broadcastUserJoin(client, user);
					}

				}
				break;

		}

		if (error >= 0) {
			Properties errAttr = new Properties(ackAttr);
			if (!e.getAttribute("sid").equals(""))
				errAttr.setProperty("sid", e.getAttribute("sid"));
			errAttr.setProperty("tag", rootTagName);
			errAttr.setProperty("error", "" + error);
			errAttr.setProperty("message", errorMessage);
			client.send(XMLUtil.simpleTag("nack", errAttr));

		} else
			if (ack) {
				attr = new Properties(ackAttr);
				attr.setProperty("tag", rootTagName);
				if (!e.getAttribute("sid").equals(""))
					attr.setProperty("sid", e.getAttribute("sid"));

				client.send(XMLUtil.simpleTag("ack", attr));
			}

		return true;
	}

	public synchronized void store(String key, String value) {
		sessionVariables.put(key, value);
	}

	public String get(String key) {
		return (String) sessionVariables.get(key);
	}

	/*** ChatProxy stuff ***/

	Hashtable users = new Hashtable(10);
	public Enumeration getUsers() {
		return users.elements();
	}

	public void unregisterUser(String nickname) throws ChatException {
		if (users.remove(nickname) == null)
			throw new ChatException(ChatException.NOSUCHNICK);
	}
	public void unregisterUser(ChatUser user) throws ChatException {
		unregisterUser(user.getNickname());
	}

	public void registerUser(ChatUser user) throws ChatException {
		if (users.get(user.getNickname()) != null)
			throw new ChatException(ChatException.NICKINUSE);

		users.put(user.getNickname(), user);
	}

	public ChatUser getUser(String nickname) throws ChatException {
		ChatUser u = (ChatUser) users.get(nickname);
		if (u == null)
			throw new ChatException(ChatException.NOSUCHNICK);
		return u;
	}

	public ChatModule() {
	}

	public ChatModule(ClientProxy proxy, ClientSession client) {
		init(proxy, client);
	}

	public void init(ClientProxy proxy, ClientSession client) {
		this.proxy = proxy;
		this.client = client;

		chatProxy = (ChatProxy) proxy.getSharedObject(SO_CHATPROXY);
		if (chatProxy == null) {
			proxy.addSharedObject(SO_CHATPROXY, this);
			chatProxy = this;
		}

		client.addSessionEventListener(this);
	}
    
	public void broadcastUserJoin(ClientSession conn, ChatUser usr) {
		Properties attr = new Properties();
		attr.setProperty("nickname", usr.getNickname());
		attr.setProperty("realname", usr.getRealname());
		attr.setProperty("other", usr.getOther());
		attr.setProperty("id", "" + conn.getId());
		proxy.broadcast(conn, XMLUtil.simpleTag("join", attr));
	}
	public void broadcastUserPart(
		ClientSession conn, 
		ChatUser usr, 
		String message) {
		Properties attr = new Properties();
		attr.setProperty("id", "" + conn.getId());
		attr.setProperty("message", message);
		attr.setProperty("nickname", usr.getNickname());

		proxy.broadcast(conn, XMLUtil.simpleTag("part", attr));

	} /**
	* Creation date: (2001-06-11 04:44:05)
	* @author: Rasmus Sten
	* @param c se.bricole.xss.server.ClientSession
	*/
	public void clientStart(ClientSession c) {
	} /*** ConnectionEventReceiver ***/
	public void clientStop(ClientSession c) {
		if (user == null)
			return;

		try {
			if (chatProxy == null) {
				return;
			} else {
				chatProxy.unregisterUser(user);
			}
		} catch (ChatException ce) {
			if (ce.getError() == ChatException.NOSUCHNICK)
				Server.warn(
					"Tried to unregister non-existant nickname " + user.getNickname() + "!"); 
			else
				Server.warn(
					"Unexcepted ChatException: error="
						+ ce.getError()
						+ ", message=\""
						+ ce.getMessage()
						+ "\""); 
		}

		broadcastUserPart(client, user, "Connection closed");

	}
}
