package se.bricole.xss.server;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

import org.apache.xerces.dom.DocumentImpl;

/**
 * This is the class used by the ClientConnection class when it wants to parse XML-documents (which is
 * all it can parse as of now).
 */
class XMLCommandParser implements CommandParser {

    public final static String vcId = "$Id$";

    private DocumentBuilder db;
    ClientProxy proxy;
    ClientSession client;
    Configuration configuration;

    Vector statefulModules = new Vector();
    Map statefulTagMap = new HashMap();

    List gsfModules = new LinkedList(); // wildcard stateful modules

    String[] statefulTags;

    public XMLCommandParser(ClientProxy proxy, ClientSession client)
    throws ParserException {
	this.proxy = proxy;
	this.client = client;
	configuration = proxy.getConfiguration();
	Vector statefulClasses = configuration.getStatefulModules();

	Enumeration e = statefulClasses.elements();
	
	while (e.hasMoreElements()) {
	    Class c = (Class) e.nextElement();
	    XMLSessionTagHandler handler = null;
	    try {
		//Server.debug(client, "instantiating stateful module " + c.toString());
		handler = (XMLSessionTagHandler) c.newInstance();
		handler.setProperties((Properties) configuration.statefulModuleProperties.get(c));
		handler.init(proxy, client);
		if ((handler.TYPE & Module.WILDCARD) == Module.WILDCARD) {
		    gsfModules.add(handler);
		} else {
		    String[] tags = handler.getTagNames();
		    for (int i = 0; i < tags.length; i++) {
			statefulTagMap.put(tags[i], handler);
			//Server.debug(client, "mapping \"" + tags[i] + "\" to " + handler.toString());
		    }
		}
		statefulModules.add(handler);
	    } catch (InstantiationException instantiationException) {
		Server.fatal("Error in instantiation of stateful module "
			     + c.toString()
			     + ": "
			     + instantiationException.getMessage()); 
	    } catch (IllegalAccessException illegalAccessException) {
		Server.fatal(illegalAccessException.getClass().toString()
			     + ": "
			     + illegalAccessException.getMessage()); 

	    } catch (ModuleException moduleException) {
		moduleException.printStackTrace();
		Server.fatal(moduleException.toString());
	    }
	}

	try {
	    db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	} catch (ParserConfigurationException ex) {
	    throw new ParserException("Initiating XML parser: " + ex.getMessage());
	}
    }

    public boolean parse(StringBuffer s) throws IOException, ParserException {
	return parse(s.toString());
    }

    private boolean parse(String s) throws IOException, ParserException {
	boolean handled = false;
	try {
	    // XXX: does not respect charset encoding (works anyway mostly)
	    Document d = db.parse(new ByteArrayInputStream(s.getBytes()));

	    Element root = d.getDocumentElement();
	    String rootName = root.getTagName();

	    String reqDomain = configuration.getTagAuthDomain(rootName);

	    if (reqDomain != null && !client.isAuthenticatedTo(reqDomain)) {
		Properties errAttr = new Properties();
		errAttr.setProperty("message", "tag required authentication");
		client.send(XMLUtil.simpleTag("auth-error", errAttr));
		return true;
	    }


	    List gslModules = configuration.getStatelessWildcardModules();

	    synchronized (gslModules) {
		Iterator i = gslModules.iterator();
		while (i.hasNext()) {
		    XMLTagHandler m = (XMLTagHandler) i.next();
		    String domain = configuration.getFilterAuthDomain(m);
		    if (domain != null) {
			if (!client.isAuthenticatedTo(domain)) {
// 			    Server.debug(client,
// 					 "[XML] [*SL] \"" + rootName + "\" !> \"" +
// 					 m.getClass().toString() + "\"");
			    continue;
			}

		    }
// 		    Server.debug(client, "[XML] [*SL] \"" + rootName + "\" -> \"" +
// 				 m.getClass().getName() + "\"");
		    handled = m.xmlTag(client, proxy, root);
		    if (handled && (m.TYPE & Module.FILTER) == Module.FILTER) {
			return handled;
		    }
		}
	    }

	    Vector v = (Vector) configuration.getStatelessTags().get(rootName);
	    if (v != null) {
		Enumeration e = v.elements();
		while (e.hasMoreElements()) {
		    XMLTagHandler h = (XMLTagHandler) e.nextElement();
		    String domain = configuration.getFilterAuthDomain(h);
		    if (domain != null) {
			if (!client.isAuthenticatedTo(domain)) {
// 			    Server.debug(client,
// 					 "[XML] [SL] \"" + rootName + "\" !> \"" +
// 					 h.getClass().toString() + "\"");
			    continue;
			}

		    }
// 		    Server.debug(client, 
// 				 "[XML] [SL] \"" + rootName + "\" -> \"" +
// 				 h.getClass().toString() + "@" +
// 				 Integer.toHexString(System.identityHashCode(h))
// 				 + "\"");
		    handled = h.xmlTag(client, proxy, root);
		    if (handled && (h.TYPE & Module.FILTER) == Module.FILTER) {
			return handled;
		    }
		}
	    }


	    synchronized (gsfModules) {
		Iterator i = gsfModules.iterator();
		while (i.hasNext()) {
		    XMLSessionTagHandler m = (XMLSessionTagHandler) i.next();

		    String domain = configuration.getFilterAuthDomain(m);
		    if (domain != null) {
			if (!client.isAuthenticatedTo(domain)) {
//  			    Server.debug(client,
// 					 "[XML] [*SF] \"" + rootName + "\" !> \"" +
// 					 m.getClass().toString() + "\"");
			    continue;
			}

		    }
// 		    Server.debug(client, 
// 				 "[XML] [*SF] \"" + rootName + "\" -> \"" +
// 				 m.getClass().getName() + "\"");
		    handled = m.xmlTag(root);
		    if (handled && (m.TYPE & Module.FILTER) == Module.FILTER) {
			return handled;
		    }
		}
	    }

	    XMLSessionTagHandler xsth = (XMLSessionTagHandler) statefulTagMap.get(rootName); 
	    if (xsth != null) {
		boolean filter = false;
		String domain = configuration.getFilterAuthDomain(xsth);
		if (domain != null) {
		    if (!client.isAuthenticatedTo(domain)) {
// 			Server.debug(client,
// 				     "[XML] [SF] \"" + rootName + "\" !> \"" +
// 				     xsth.getClass().toString() + "\"");
			filter = true;
		    }
		    
		}
		if (!filter) {
// 		    Server.debug(client, 
// 				 "[XML] [SF] \"" + rootName + "\" -> \"" +
// 				 xsth.getClass().toString() + "\""); 
		    handled = xsth.xmlTag(root);
		}
	    }
	    if (client.isInDelayedFinish())
		client.finish();

	    if (!handled) {
		if (configuration.getBooleanProperty("BroadcastUnhandledTags")) {
		    Server.debug(client, 
				 "Broadcasting unhandled tag \"" + rootName + "\""); 		    
		    proxy.broadcast(client, s, true);
		} else {
		    Server.debug(client, 
				 "Unhandled (but parsed) XML, root name \"" + rootName + "\""); 
		}
	    }

	} catch (SAXException ex) {
	    Properties errAttr = new Properties();
	    errAttr.setProperty("error", "0");
	    errAttr.setProperty("message", "Bad XML (see server debug log)");
	    client.send(XMLUtil.simpleTag("nack", errAttr));
	    throw new ParserException("XML parsing: " + ex.getMessage());
	} catch (DOMException dex) { // only thrown when creating the reply DOM
	    throw new ParserException("Reply generation: " + dex.getMessage());
	} catch (ModuleException mex) {
	    Server.warn("Module exception: " + mex);
	    mex.printStackTrace();
	}

	return handled;
    }

}
