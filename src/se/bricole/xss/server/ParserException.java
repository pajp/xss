package se.bricole.xss.server;

/**
 * A ParserException is thrown whenever the XML parser failed to generate a valid document from supplied XML data.
 */
public class ParserException extends Exception {
    public final static String vcId = "$Id: ParserException.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";

    public ParserException() {}
    
    public ParserException(String s) {
	super(s);
    }

}
