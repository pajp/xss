package se.bricole.xss.server;

import org.apache.xerces.dom.DocumentImpl;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.apache.xml.serialize.OutputFormat;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import java.util.Enumeration;
import java.util.Properties;
import java.io.IOException;
import java.io.StringWriter; 

import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * This class keeps static utility method to make it easier to create and handle
 * XML objects.
 *
 * Creation date: (2001-04-17 21:04:10)
 * @author Rasmus Sten
 * @version $Id: XMLUtil.java,v 1.3 2002/09/12 00:00:34 pipeman Exp $
*/
public class XMLUtil {

    public final static String vcId = "$Id: XMLUtil.java,v 1.3 2002/09/12 00:00:34 pipeman Exp $";
    
    public static Element appendResultSetToNode(Element root, String rowTag, ResultSet rs)
    throws SQLException {
	Document doc = root.getOwnerDocument();
	
	ResultSetMetaData meta = rs.getMetaData();
	int columnCount = meta.getColumnCount();
	int rowCount = 0;
	while (rs.next()) {
	    Element rowElement = doc.createElement(rowTag);
	    rowElement.setAttribute("row", ""+rowCount);
	    for (int i=1; i <= columnCount; i++) {
		rowElement.setAttribute(meta.getColumnName(i),
					rs.getString(i));		
	    }
	    rowCount++;
	    root.appendChild(rowElement);	    
	}
	
	return root;
    }

    public static String resultSetToXML(String containerTag, String rowTag, ResultSet rs)
    throws SQLException {
	Document doc = new DocumentImpl();
	Element root = doc.createElement(containerTag);
	appendResultSetToNode(root, rowTag, rs);
	doc.appendChild(root);
	return documentToString(doc);
    }


    /**
     * Generates a String from a Document object (using the
     * current system locale and omits XML declaration)
     */
    public static String documentToString(Document d) {
	OutputFormat format = new OutputFormat(d);
	format.setOmitXMLDeclaration(true);
	StringWriter stringOut = new StringWriter();
	XMLSerializer serializer = new XMLSerializer(stringOut, format);

	try {
	    serializer.asDOMSerializer();
	    serializer.serialize(d.getDocumentElement());
	} catch (IOException e) {
	    throw new RuntimeException("fuckin shit!");
	}
	return stringOut.toString();
    }

    /**
     * <p>Generates a simple XML tag use the supplies Properties
     * object to generated the tag attributes, and returns a
     * String representation.
     * </p>
     * Sample usage:<pre>
     * Properties p = new Properties();
     * p.setProperty("attribute", "value");
     * System.out.println(XMLUtil.simpleTag("dummy-tag", p));
     * </pre>
     * Output: <tt>&lt;dummy-tag attribute="value"/&gt;</tt>
     */
    public static String simpleTag(String tagName, Properties attributes)
	throws DOMException {
	Enumeration names = attributes.propertyNames();
	Document doc = new DocumentImpl();
	Element root = doc.createElement(tagName);

	while (names.hasMoreElements()) {
	    String property = (String) names.nextElement();
	    root.setAttribute(property, attributes.getProperty(property));
	}

	doc.appendChild(root);
	return documentToString(doc);
    }

    public static Document newXML()
    throws DOMException {
	return new DocumentImpl();
    }
}
