package se.bricole.xss.modules;

import se.bricole.xss.server.*;

import org.w3c.dom.*;

import java.sql.*;
import java.util.*;
import java.io.*;

public class DatabaseAccess implements XMLTagHandler, ModuleRegistrar {

    File propertyFile;
    Properties properties;
    Configuration config;

    /**
     * Set the Properties value.
     * @param newProperties The new Properties value.
     */
    public void setProperties(Properties newProperties) {
	this.properties = newProperties;
	init();
    }

    public void setConfiguration(Configuration config) {
	this.config = config;
    }

    private void init() {
	
    }

    public String[] getTagNames() {
	return null;
    }

    public boolean xmlTag(ClientSession session, ClientProxy proxy, Element element) {
	return false;
    }
    
}
