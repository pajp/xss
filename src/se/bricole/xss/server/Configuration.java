package se.bricole.xss.server;

import javax.xml.parsers.*;
import java.io.*;
import java.util.Vector;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

import org.apache.xerces.dom.DocumentImpl;

import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.LinkedList;


/**
 * Handles loading of configuration and modules.
 *
 * $Id: Configuration.java,v 1.6 2002/09/14 17:08:29 gray Exp $
 */
public class Configuration extends java.util.Properties {

    public final static String vcId = "$Id: Configuration.java,v 1.6 2002/09/14 17:08:29 gray Exp $";

    Object moduleRegistryLock = new Object();

    Map statelessTags = new HashMap();
    Map statefulTags = new HashMap();

    /*
     * Changes in the authentication system: a module can have the following props:
     * "authentication": the realm identifying the authentication module
     *
     * "authentication-type": "filter" or "reject". "filter" means that no messages
     * will get through to the module (thus are simply ignored) as long as the user
     * isn't authenticated to the given realm. "reject" on the other hand responds
     * with an "auth-error" message.
     * "reject" authentication does not work on wildcard modules since the server
     * passes all requests to them.
     *
     * "authentication-method": "basic" or "md5". "basic" means that the password
     * will be sent in plain text, while "md5" is a challenge-respond method 
     * involving a little more work in the client (such as implementing MD5 :->
     * which is notoriously missing in Flash ActionScript).
     */
    Map modulesAuthFilter = new HashMap();
    Map tagsAuthRequired = new HashMap();
    Map authHandlers = new HashMap();

    Vector statefulModules = new Vector(); // XXX: Why Vector?
    
    List statelessWildcardModules = new LinkedList();
    List statefulWildcardModules = new LinkedList();

    File configFile = null;

    ServerManager manager;

//     public Configuration() {
// 	super();
//     }

    public Configuration(String file, ServerManager manager) {
	this.manager = manager;

	/** default configuration **/
	setProperty("ListenPort", "8085");
	setProperty("ListenAddress", "127.0.0.1");
	setProperty("UsersPerProxy", "100");
	setProperty("ProxiesPerServer", "10");
	setProperty("GCInterval", "120");
	setProperty("PrintStatus", "60");
	setProperty("MaxPingTime", "60");
	setProperty("DieOnBadModule", "1");
	setProperty("ClientIdleTimeout", "3600");
	setProperty("InitialThreadPool", "10");
	setProperty("GrowableThreadPool", "0");
	setProperty("BroadcastUnhandledTags", "false");

	String[] singleTextTags = 
	{
	    "ListenPort", 
	    "UsersPerProxy", 
	    "ProxiesPerServer", 
	    "GCInterval", 
	    "PrintStatus", 
	    "MaxPingTime", 
	    "DieOnBadModule", 
	    "ClientIdleTimeout", 
	    "InitialThreadPool",
	    "GrowableThreadPool"
	}; 

	StringBuffer data = new StringBuffer(100);

	DocumentBuilder db;

	try {
	    configFile = new File(file);
	    BufferedReader in = new BufferedReader(new FileReader(configFile));
	    Server.status("Reading configuration from file \"" + file + "\"");
	    String s = new String();
	    while ((s = in.readLine()) != null)
		data.append(s);

	    in.close();
	} catch (FileNotFoundException fnfe) {
	    Server.fatal("File not found: " + file);
	} catch (IOException ioe) {
	    Server.fatal("I/O exception: " + ioe.getMessage());
	}

	try {
	    db = DocumentBuilderFactory.newInstance().newDocumentBuilder();

	    Document doc = db.parse(new ByteArrayInputStream(data.toString().getBytes()));
	    Element root = doc.getDocumentElement();
	    if (!root.getTagName().equals("XSSConfig")) {
		Server.fatal("Configuration: root must be <XSSConfig/>");
	    }

	    NodeList configs = root.getChildNodes();
	    Properties globalModuleProperties = new Properties();

	    // used by the ECMAScript module to find out where it 
	    // should search for script directories.
	    globalModuleProperties.setProperty("xss.config.file", configFile.getAbsolutePath());
	    for (int i = 0; i < configs.getLength(); i++) {
		Node n = configs.item(i);
		boolean _handled = false;
		if (n.getNodeName().startsWith("#"))
		    _handled = true; // just crap. :-)
		for (int j = 0; j < singleTextTags.length; j++) {
		    if (n.getNodeName().equals(singleTextTags[j])) {
			try {
			    Integer.parseInt(getChildText(n));
			} catch (NumberFormatException ex) {
			    Server.fatal("\"" + n.getNodeName() + "\" contains non-numeric value");
			}
			setProperty(n.getNodeName(), getChildText(n));
			_handled = true;
		    }
		}
		if (n.getNodeName().equals("ListenAddress")) {
		    setProperty(n.getNodeName(), getChildText(n)); 
		    _handled = true; 
		}
		if (n.getNodeName().equals("BroadcastUnhandledTags")) {
		    setProperty(n.getNodeName(), getChildText(n)); 
		    _handled = true; 
		}
		if (n.getNodeName().equals("GlobalModuleProperties")) {
		    _handled = true;
		    NodeList propNodes = n.getChildNodes();
		    for (int j=0; j < propNodes.getLength(); j++) {
			Node pn = propNodes.item(j);
			if (!pn.getNodeName().equals("Property") ||
			    pn.getNodeType() != Node.ELEMENT_NODE) continue;
			Element e = (Element) pn;
			globalModuleProperties.setProperty(e.getAttribute("name"),
							   e.getAttribute("value"));

		    }
		}
		if (n.getNodeName().equals("Modules")) {
		    _handled = true;
		    NodeList modNodes = n.getChildNodes();
		    Server.status("Loading modules...");
		    int moduleFailCount = 0;
		    for (int j = 0; j < modNodes.getLength(); j++) {
			Node mn = modNodes.item(j);
			if (mn.getNodeName().equals("Module")
			    && mn.getNodeType() == Node.ELEMENT_NODE) {
			    Element e = (Element) mn;
			    String source = e.getAttribute("source");
			    String name = e.getAttribute("name");
			    String auth = e.getAttribute("authentication");
			    String authType = e.getAttribute("authentication-type");
			    String authMethod = e.getAttribute("authentication-method");
			    NodeList modChilds = mn.getChildNodes();
			    Properties modProperties = new Properties(globalModuleProperties);
			    for (int childNo=0; childNo < modChilds.getLength(); childNo++) {
				Node pn = modChilds.item(childNo);
				if (pn.getNodeType() == Node.ELEMENT_NODE &&
				    pn.getNodeName().equals("PropertyFile")) {
				    try {
					String propFileName = ((Element) pn).getAttribute("name");
					File propFile = new File(propFileName);
					if (!propFile.exists()) {
					    propFile = new File(configFile.getAbsolutePath(), propFileName);
					    if (!propFile.exists())
						throw new IOException("Property file \"" +
								      propFile + "\" not found");
											  
					}
					Properties fileProperties = new Properties();
					InputStream is = new FileInputStream(propFile);
					fileProperties.load(is);
					is.close();
					modProperties.putAll(fileProperties);
				    } catch (IOException ex1) {
					moduleFailCount++;
					moduleLoadException(name, source, new ModuleException(ex1));
					continue;
				    }
				}
				if (pn.getNodeType() == Node.ELEMENT_NODE &&
				    pn.getNodeName().equals("Property")) {
				    Element pe = (Element) pn;
				    modProperties.setProperty(pe.getAttribute("name"),
							      pe.getAttribute("value"));
				}
			    }
			    try {
				loadModule(source, name, auth, authType, authMethod, modProperties);
				Server.status("Module \"" + name + "\" loaded");
			    } catch (ModuleException me) {
				moduleFailCount++;
				moduleLoadException(name, source, me);
			    }
			} // if (mn.getNodeName().equals("Module")...

		    } // for...
		    if (moduleFailCount > 0 && getIntProperty("DieOnBadModule") == 1) {
			Server.fatal(moduleFailCount + " module(s) failed to load, shutting down.");
		    }

		} // if (n.getNodeName().equals("Modules")) {
		if (!_handled)
		    Server.warn("Unknown configuration tag \"" + n.getNodeName() + "\"");
	    } // for...

	} catch (IOException ioe) {
	    Server.fatal("I/O error: " + ioe.getMessage());
	} catch (ParserConfigurationException ex) {
	    Server.fatal("Initiating XML parser: " + ex.getMessage());
	} catch (SAXException saxe) {
	    Server.fatal("Parsing config file: " + saxe.getMessage());
	}
    }

    private void moduleLoadException(String name, String source, ModuleException me) {
	Server.warn("Error loading module \"" + name + "\" from \"" + source + "\":");
	if (me.getException() != null) {
	    Server.warn("\tException: " + me.getException().getClass().toString());
	    Server.warn("\tMessage: " + me.getException().getMessage());
	    me.printStackTrace();
	} else {
	    Server.warn("\tMessage: " + me.getMessage());
	}
    }

    /**
     * Shuts down the XSS, but not the JVM.
     *
     * If non-daemon threads are alive, they will make the JVM
     * live on.
     */ 
    public void shutdown() {
	manager.shutdown(false);
    }

    /**
     * Makes an efford to restart the XSS.
     *
     * This is not a full restart and does not currently reload the
     * configuration file.
     */
    public void restart() {
	manager.restart();
    }

    public long getUptime() {
	return manager.getUptime();
    }

    public File getConfigurationFile() {
	return configFile;
    }

    protected String getFilterAuthDomain(Module m) {
	return (String) modulesAuthFilter.get(m);
    }

    private void registerModule(Class moduleClass, String auth,
				String authType, String authMethod,
				Properties properties) throws ModuleException {

    }

    /**
     * Associated an initialized XML module with a tag and its
     * optional authentication domain.
     */
    public void associate(Module module, String tag, String auth, String authType)
    throws ModuleException, IllegalAccessException, NoSuchFieldException {
	synchronized (moduleRegistryLock) {
	    int type = module.getClass().getField("TYPE").getInt(null);
	    if (authType != null && authType.equals("filter")) {
		modulesAuthFilter.put(module, auth);
	    }
	    if ((type & Module.STATELESS) == Module.STATELESS) {
		Vector v = (Vector) statelessTags.get(tag);
		if (v == null) v = new Vector();
		v.add(module);
		statelessTags.put(tag, v);
		if (auth != null && !auth.equals("")) {
		    tagsAuthRequired.put(tag, auth);
		}
	    } 
	    if ((type & Module.SESSION) == Module.SESSION) {
		statefulModules.remove(module.getClass());
		statefulModules.add(module.getClass());

		statefulTags.put(tag, module.getClass());
		if (auth != null && !auth.equals("")) {
		    tagsAuthRequired.put(tag, auth);
		}
	    }

	}
    }

    /**
     * Registers an initialized XML wildcard module and its optional
     * authentication domain. Note that wildcard XML modules can only
     * require authentication to "filter" type authenticators as we
     * wouldn't know if we should pass the calls to them since they
     * don't have any tags associated to them.
     */
    public void associateWildcard(Module module, String auth, String authType, String authMethod,
				  Properties properties)
    throws ModuleException, IllegalAccessException, NoSuchFieldException {
	synchronized (moduleRegistryLock) {
	    int type = module.getClass().getField("TYPE").getInt(null);
	    if (authType != null && authType.equals("filter")) {
		modulesAuthFilter.put(module, auth);
	    }
	    if ((type & Module.SESSION) == Module.SESSION) {
		statefulWildcardModules.remove(module);
		statefulWildcardModules.add(module);
	    }
	    if ((type & Module.STATELESS) == Module.STATELESS) {
		statelessWildcardModules.remove(module);
		statelessWildcardModules.add(module);
	    }
	}
    }

    
    /**
     * Load a module with the default class loader
     * (<code>Class.forName(name)</code>).
     */
    private void loadModuleFromClasspath(String name, String auth,
					 String authType, String authMethod,
					 Properties properties) throws ModuleException {
	try {
	    Class c = Class.forName(name);
	    // we can use reflection to find out implemented classes,
	    // but this feels more efficient (although not as secure)
	    // why not "instanceof"? 
	    int moduleType = c.getField("TYPE").getInt(null);

	    Module m = (Module) c.newInstance();
	    if (m instanceof ModuleRegistrar) {
		((ModuleRegistrar) m).setConfiguration(this);
	    }
	    m.setProperties(properties);
	    if ((moduleType & Module.XML) == Module.XML) {
		String[] tags = ((XMLModule) m).getTagNames();
		for (int i=0; tags != null && i < tags.length; i++) {
		    associate(m, tags[i], auth, authType);
		}

		if ((moduleType & Module.WILDCARD) == Module.WILDCARD) {
		    associateWildcard(m, auth, authType, authMethod, properties);
		}
	    }

	    if ((moduleType & Module.AUTH) == Module.AUTH) {
		AuthHandler authModule = (AuthHandler) m;
		String[] domains = authModule.getAuthDomains();
		for (int i=0; i < domains.length; i++) {
		    authHandlers.put(domains[i], authModule);
		}
	    }
	} catch (ClassCastException ce) {
	    throw new ModuleException(ce);
	} catch (ClassNotFoundException cce) {
	    throw new ModuleException(cce);
	} catch (InstantiationException ie) {
	    throw new ModuleException(ie);
	} catch (IllegalAccessException iae) {
	    throw new ModuleException(iae);
	} catch (NoSuchFieldException nsfe) {
	    throw new ModuleException(nsfe);
	}

    }

    public void loadModule(String source, String name, String auth,
			   String authType, String authMethod,
			   Properties properties) throws ModuleException {
	if (source.equals("classpath")) {
	    loadModuleFromClasspath(name, auth, authType, authMethod, properties);
	} else {
	    throw new ModuleException("Unknown source method \"" + source + "\"");
	}
    }

    public Map getStatefulTags() {
	return statefulTags;
    }
	
    public Map getStatelessTags() {
	return statelessTags;
    }

    public AuthHandler getAuthenticatorByTag(String tag) {
	String tagDomain = (String) tagsAuthRequired.get(tag);
	if (tagDomain == null) return null;
	return (AuthHandler) authHandlers.get(tagDomain);
    }

    public AuthHandler getAuthenticatorByDomain(String domain) {
	return (AuthHandler) authHandlers.get(domain);
    }

    public boolean tagRequiresAuthentication(String tag) {
	return tagsAuthRequired.containsKey(tag);
    }

    public String getTagAuthDomain(String tag) {
	return (String) tagsAuthRequired.get(tag);
    }

    public Vector getStatefulModules() {
	return statefulModules;
    }
	
    static int getChildInt(Node n) throws NumberFormatException {

	return Integer.parseInt(getChildText(n));
    }

    static String getChildText(Node n) {
	return n.getFirstChild().getNodeValue();
    }

    public boolean getBooleanProperty(String s) {
	String p = getProperty(s);
	if (p == null) return false;
	if ("true".equals(p)) return true;
	return false;
    }

    public int getIntProperty(String s) {
	try {
	    return Integer.parseInt(getProperty(s));
	} catch (NumberFormatException e) {
	    throw new IllegalStateException(
					    "the property \""
					    + s
					    + "\" contains "
					    + "non-numeric value \""
					    + getProperty(s)
					    + "\""); 
	}
    }
    public String getStringProperty(String s) {
	return getProperty(s);
    }

    /**
     * Get the StatelessWildcardModules value.
     * @return the StatelessWildcardModules value.
     */
    public List getStatelessWildcardModules() {
	return statelessWildcardModules;
    }

    /**
     * Get the StatefulWildcardModules value.
     * @return the StatefulWildcardModules value.
     */
    public List getStatefulWildcardModules() {
	return statefulWildcardModules;
    }

}
