package se.bricole.xss.server;

import org.w3c.dom.*;
import java.util.*;

import java.io.*;
//import FESI.jslib.*;

import org.mozilla.javascript.*;


public class ECMAScriptModule implements XMLTagHandler {

    public static int TYPE = XML | WILDCARD | STATELESS;

    Properties properties;

    Scriptable scope;

    boolean autoReload = false;

    Map jsTagObjects = new HashMap();
    Map tagFiles = new HashMap();
    Map fileTimes = new HashMap();

    Thread rescanThread;

    File scriptDir;

    Object scriptMutex = new Object();

    long autoRescanInterval = 0;

    public ECMAScriptModule() throws ModuleException {
	try {
	    Context context = Context.enter();
	    scope = context.initStandardObjects(null);
	    XMLUtil xmlutil = new XMLUtil();
	    scope.put("XMLUtil", scope, Context.toObject(xmlutil, scope));
	} finally {
	    Context.exit();
	}
    }

    /**
     * Set the Properties value.
     * @param newProperties The new Properties value.
     */
    public void setProperties(Properties newProperties)
    throws ModuleException {
	this.properties = newProperties;
	Server.debug("ECMAScriptModule properties: " + properties);
	initModule();
    }

    private void initModule() throws ModuleException {
	initModule(false);
    }
    private void initModule(boolean silent) throws ModuleException {
	String scriptDirName = properties.getProperty("scriptDirectory");

	String autoReloadStr = properties.getProperty("autoReload");
	if (autoReloadStr != null && autoReloadStr.equals("true")) {
	    autoReload = true;
	    if (!silent) {
		Server.status("Enabling auto-reloading of Javascript files.");
	    }
	}

	if (scriptDirName == null || !(new File(scriptDirName).exists())) {
	    if (!silent) {
		Server.warn("ECMAScriptModule: scriptDirectory property not defined " +
			    "or directory is missing");
		Server.warn("ECMAScriptModule: scriptDirectory == \"" + 
			    scriptDirName + "\"");
	    }
	    scriptDirName = new File(new File(properties.getProperty("xss.config.file")).
				     getParentFile(),
				     scriptDirName).getAbsolutePath();
	}

	String autoRescanStr = properties.getProperty("autoRescan");

	if (autoRescanStr != null && autoRescanStr.equals("true")) {
	    String autoRescanIntervalStr = properties.getProperty("autoRescanInterval");
	    if (autoRescanIntervalStr == null)
		autoRescanIntervalStr = "2"; // default value
	    try {
		autoRescanInterval = Long.parseLong(autoRescanIntervalStr)*1000l;
	    } catch (NumberFormatException ex1) {
		throw new ModuleException("Bad autoRescanInterval value " + ex1.getMessage());
	    }
	    if (rescanThread == null || !rescanThread.isAlive()) {
		rescanThread = new Thread() {
			boolean active = true;
			public void run() {
			    while (active) {
				try {
				    Thread.sleep(autoRescanInterval);
				    initModule(true);
				} catch (InterruptedException ex1) {
				    active = false;
				} catch (ModuleException ex2) {
				    Server.warn("Module re-initialization error: " +
						ex2.toString());
				    ex2.printStackTrace();
				}
				if (isInterrupted()) active = false;
			    }
			}
		    };
		rescanThread.setDaemon(true);
		rescanThread.setName("RescanThread");
		rescanThread.start();
	    }
	} else {
	    rescanThread.interrupt();
	}
	
	scriptDir = new File(scriptDirName);
	if (!silent)
	    Server.status("Using ECMAScript directory \"" +
			  scriptDir.getAbsolutePath() + "\"");
	if (!scriptDir.exists()) {
	    throw new ModuleException("Script directory \"" +
				      scriptDir.getAbsolutePath() + "\" not found.");
	}

	File[] scripts = scriptDir.listFiles();
	for (int i = 0; i < scripts.length; i++) {
	    if (scripts[i].getName().endsWith(".es") ||
		scripts[i].getName().endsWith(".js")) {
		loadScript(scripts[i]);
	    }
	}

    }

     private Scriptable loadScript(File script) throws ModuleException {
	Scriptable containerObj = null;
	boolean alreadyloaded = false;
	Context context = Context.enter();

	try {
	    if (autoReload) {
		synchronized (scriptMutex) {
		    Long loadedAt = (Long) fileTimes.get(script);
		    if (loadedAt != null && 
			script.lastModified() <= loadedAt.longValue()) {
			alreadyloaded = true;
		    }
		}
	    }
	    FileInputStream is = new FileInputStream(script);
	    InputStreamReader reader = new InputStreamReader(is, "ISO-8859-1");


	    containerObj = context.newObject(scope);
	    containerObj.setPrototype(scope);
	    containerObj.setParentScope(scope);
	    context.evaluateReader(containerObj, reader,
				   script.getAbsolutePath(),
				   0, null);


	    List tagNames = new LinkedList();
	    Object tagNameObj = containerObj.get("tag", containerObj);
	    if (tagNameObj != null && tagNameObj != Scriptable.NOT_FOUND) {
		String tagName = Context.toString(tagNameObj.toString());
		tagNames.add(tagName);
	    }

	    Object tagList = containerObj.get("tags", containerObj);
	    if (tagList != null && tagList != Scriptable.NOT_FOUND) {
		Number tagListLength = (Number) ((Scriptable)tagList).get("length", (Scriptable) tagList);
		for (int i=0; i < tagListLength.intValue(); i++) {
		    tagNames.add(Context.toString(((Scriptable)tagList).get(i, (Scriptable) tagList)));
		}
	    }

	    if (tagNames.size() == 0) {
		throw new ModuleException("Malformed or no \"tag\" or \"tags\" member in " + 
					  "script \"" + script.getName() + "\"");
	    }

	    // just to make sure the onXML function is there?
	    Object functionObj = containerObj.get("onXML", containerObj);
	    if (functionObj == null || functionObj == Scriptable.NOT_FOUND) {
		throw new ModuleException("onXML property null or not defined in \"" +
					  script.getAbsolutePath() + "\"");
	    }

	    synchronized (scriptMutex) {
		Iterator i = tagNames.iterator();
		while (i.hasNext()) {
		    String tagName = (String) i.next();
		    jsTagObjects.put(tagName, containerObj);
		    
		    if (autoReload) {
			tagFiles.put(tagName, script);
			fileTimes.put(script, new Long(System.currentTimeMillis()));
		    }
		}
	    }

	} catch (IOException ex1) {
	    throw new ModuleException("I/O error reading \"" + 
				      script.getAbsolutePath() + "\"",
				      ex1);
	} catch (JavaScriptException ex2) {
	    Server.warn("ECMAScript evalutation error: " + ex2);
	} catch (PropertyException ex3) {
	    Server.warn("ECMAScript evalutation error: " + ex3);
	    ex3.printStackTrace();
	} catch (NotAFunctionException ex4) {
	    Server.warn("ECMAScript evalutation error: " + ex4);
	    ex4.printStackTrace();

	} finally {
	    Context.exit();
	}

	
	String scriptName = script.getName().substring(0, script.getName().length()-3);
	if (!alreadyloaded) {
	    Server.status("Loaded ECMAScript \"" + script.getName() + "\".");
	}
	return containerObj;
    }

    public String[] getTagNames() {
	return null;
    }


    public boolean xmlTag(ClientSession client,
			  ClientProxy proxy,
			  Element element) throws ParserException, IOException, ModuleException {
	String rootName = element.getTagName();
	Scriptable script;
	Context context = Context.enter();
	synchronized (scriptMutex) {
	    script = (Scriptable) jsTagObjects.get(rootName);
	}
	if (script == null) return false;

	if (autoReload) {
	    File scriptFile;
	    Long loadedAt;
	    synchronized (scriptMutex) {
		scriptFile = (File) tagFiles.get(rootName);
		loadedAt = (Long) fileTimes.get(scriptFile);
	    }
	    if (scriptFile.lastModified() > loadedAt.longValue()) {
		Server.status("Reloading Javascript file \"" +
			      scriptFile.getAbsolutePath() + "\"");
		script = loadScript(scriptFile);
	    }
	}

	boolean handled = false;
	try {
	    Object fObj = script.get("onXML", script);
	    if (!(fObj instanceof Function)) {
		throw new ModuleException("onXML object is not a function");
	    }
	    Object[] args = { client, proxy, element };
	    Function func = (Function) fObj;
	    Object obj = func.call(context, scope, script, args);
	    if (obj instanceof NativeJavaObject) {
		obj = ((NativeJavaObject) obj).unwrap();
	    }

	    if (obj instanceof String) {
		client.send((String)obj);
		handled = true;
	    }

	    // to make this scale to larger XML objects, we should
	    // probably find a way to make the serialization stream 
	    // the data to the client instead of building up a big
	    // String object
	    if (obj instanceof Document) {
		String res = XMLUtil.documentToString((Document) obj);
		Server.debug("sending XML to client: " + res);
		client.send(res);

		handled = true;
	    }

	    if (obj instanceof Boolean) {
		handled = (((Boolean) obj).booleanValue());
	    }


	    if (!handled) {
		Server.warn("Javascript onXML function returned unknown type " +
			    obj.getClass().getName());
	    }
	} catch (JavaScriptException ex1) {
	    Server.warn("Javascript execution error: " + ex1.getMessage());
	    ex1.printStackTrace();
	} finally {
	    Context.exit();
	}
	return handled;
    }
}
