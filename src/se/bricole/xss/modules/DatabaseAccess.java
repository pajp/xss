package se.bricole.xss.modules;

import se.bricole.xss.server.*;

import org.w3c.dom.*;

import java.sql.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;


public class DatabaseAccess implements XMLTagHandler, ModuleRegistrar {

    File propertyFile;
    Properties jdbcProperties = new Properties();
    Properties properties;
    Map queries = new HashMap();
    Configuration config;
    String dbURL;
    String tagPrefix;
    String[] myTags = null;
    String[] myBaseTags = { "executeQuery", "executeUpdate" };

    final static int TAG_QUERY = 0;
    final static int TAG_UPDATE = 1;


    /**
     * Set the Properties value.
     * @param newProperties The new Properties value.
     */
    public void setProperties(Properties newProperties)
    throws ModuleException {
	this.properties = newProperties;
	init();
    }

    public void setConfiguration(Configuration config) {
	this.config = config;
    }

    private void init() throws ModuleException {
	dbURL = properties.getProperty("jdbcURL");
	tagPrefix = properties.getProperty("prefix");
	if (tagPrefix == null) tagPrefix = "db";
	jdbcProperties.setProperty("user", properties.getProperty("jdbcUsername"));
	jdbcProperties.setProperty("password", properties.getProperty("jdbcPassword"));
	myTags = new String[myBaseTags.length];
	for (int i=0; i < myTags.length; i++) {
	    myTags[i] = tagPrefix + ":" + myBaseTags[i];
	    try {
		// XXX: how handle authentication? 
		config.associate(this, myTags[i], null, null);
	    } catch (Exception ex1) {
		throw new ModuleException(ex1);
	    }
	}

	try {
	    Class.forName(properties.getProperty("jdbcDriver"));
	} catch (Exception ex1) {
	    throw new ModuleException("Error loading JDBC driver: " + ex1.toString());
	}

	propertyFile = new File(properties.getProperty("queryFile"));
	if (!propertyFile.exists()) {
	    propertyFile = new File(config.getConfigurationFile().getParentFile(),
				    properties.getProperty("queryFile"));
	}
	if (!propertyFile.exists()) {
	    throw new ModuleException("query file \"" +
				      propertyFile.getAbsolutePath() +
				      "\" not found");
	}
	try {
	    loadQueries();
	} catch (IOException ex1) {
	    ex1.printStackTrace();
	    throw new ModuleException("Error loading " + 
				      propertyFile.getAbsolutePath(), ex1);
	}
    }

    //
    // TODO: throw an error if type is missing in a parameter defintion
    // (ie, it's only a '?').
    //
    void loadQueries() throws IOException, ModuleException {
	Properties queryStrings = new Properties();
	InputStream is = new FileInputStream(propertyFile);
	queryStrings.load(is);
	is.close();

	Set entrySet = queryStrings.entrySet();
	Iterator i = entrySet.iterator();
	while (i.hasNext()) {
	    Map.Entry entry = (Map.Entry) i.next();
	    Server.debug("analyzing entry " + entry);
	    String queryName = (String) entry.getKey();
	    String query = (String) entry.getValue();
	    Pattern fieldPattern = Pattern.compile("\\?\\{(\\p{Alpha}+)\\}");
	    Matcher matcher = fieldPattern.matcher(query);

	    List types = new LinkedList();
	    while (matcher.find()) {
		String typeName = matcher.group(1);
		if (typeName.equals("String")) {
		    types.add(new Integer(Query.VAR_STRING));
		} else if (typeName.equals("int")) {
		    types.add(new Integer(Query.VAR_INT));
		} else {
		    throw new ModuleException("Unknown SQL type \"" + typeName + "\"");
		}
	    }
	    query = matcher.replaceAll("?");
	    Query q = new Query(query, types);
	    Server.debug("Created Query: " + q.toString());
	    synchronized (queries) {
		queries.put(queryName, q);
	    }
	}
    }

    public String[] getTagNames() {
	return null;//return myTags;
    }

    public boolean xmlTag(ClientSession session, ClientProxy proxy, Element element)
    throws ModuleException, IOException {
	String rootName = element.getTagName();
	int tagNo = -1;
	for (int i=0; tagNo == -1 && i < myTags.length; i++) {
	    if (myTags[i].equals(rootName)) {
		tagNo = i;
	    }
	}

	if (tagNo == -1) return false;

	/*
	 * Query definition format:
	 * SELECT * FROM table WHERE name = ?{String} AND id = ?{int}
	 */


	String queryName = element.getAttribute("query");
	if (queryName == null)
	    throw new ModuleException("Missing \"query\" attribute");
	Query query;
	synchronized (queries) {
	    query = (Query) queries.get(queryName);
	}
	if (query == null) {
	    throw new ModuleException("Undefined query \"" + queryName + "\"");
	}
	
	Connection c = null;
	try {
	    c = getConnection();
	    PreparedStatement ps = c.prepareStatement(query.getSQL());
	    for (int i=0; i < query.getVariableCount(); i++) {
		String variableString = element.getAttribute("var" + (i+1));
		int type = query.getType(i);
		if (type == Query.VAR_INT) {
		    ps.setInt((i+1), Integer.parseInt(variableString));
		} else if (type == Query.VAR_STRING) {
		    ps.setString((i+1), variableString);
		}
	    }

	    Document doc = XMLUtil.newXML();
	    if (tagNo == TAG_QUERY) {
		Server.debug("Executing SQL query: " + ps);
		ResultSet rs = ps.executeQuery();
		ResultSetMetaData metaData = rs.getMetaData();
		Element root = doc.createElement("ResultSet");
		while (rs.next()) {
		    Element rowNode = doc.createElement("Row");
		    for (int i=0; i < metaData.getColumnCount(); i++) {
			rowNode.setAttribute(metaData.getColumnName(i+1),
					     rs.getString(i+1));
		    }
		    root.appendChild(rowNode);
		}
		doc.appendChild(root);
	    } else if (tagNo == TAG_UPDATE) {
		Server.debug("Executing SQL query: " + ps);
		int affectedRows = ps.executeUpdate();
		Element root = doc.createElement("UpdateStatus");
		root.setAttribute("affectedRows", "" + affectedRows);
		doc.appendChild(root);
	    } else {
		throw new ModuleException("Internal error: unknown tag id " + tagNo);
	    }
	    
	    session.send(doc);

	} catch (NumberFormatException ex2) {
	    throw new ModuleException("Error parsing variable number \"" + ex2.getMessage() + "\"");
	} catch (SQLException ex1) { // send to client?
	    ex1.printStackTrace();
	    throw new ModuleException("SQL error: \"" + ex1.getMessage() + "\"");
	} finally {
	    release(c);
	}
	return true;
    }

    void release(Connection c) {
	try {
	    if (c != null) c.close();
	} catch (Exception ex1) {
	    ex1.printStackTrace();
	}
    }
    Connection getConnection() throws SQLException {
	return DriverManager.getConnection(dbURL, jdbcProperties);
    }

    
    /**
     * Holds a query and information about its SQL parameter types.
     */
    static class Query {
	String sql;
	List variableTypes;
	final static int VAR_STRING = 1;
	final static int VAR_INT = 2;
	public Query(String sql, List types) {
	    this.sql = sql;
	    variableTypes = new ArrayList(types);
	    ((ArrayList) variableTypes).trimToSize();
	}

	public int getType(int index) {
	    return ((Integer) variableTypes.get(index)).intValue();
	}

	public int getVariableCount() {
	    return variableTypes.size();
	}

	public String getSQL() {
	    return sql;
	}
	public String toString() {
	    return "[Query <" + sql + ">, " + variableTypes.size() + " variables]";
	}
    }
    
}
