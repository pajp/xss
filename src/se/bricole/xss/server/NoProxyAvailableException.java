package se.bricole.xss.server;

/**
 * Creation date: (2001-06-11 19:29:33)
 * @author: Rasmus Sten
 */
public class NoProxyAvailableException extends Exception {

    public final static String vcId = "$Id: NoProxyAvailableException.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";

    /**
     * NoProxyAvailableException constructor comment.
     */
    public NoProxyAvailableException() {
	super();
    }
    /**
     * NoProxyAvailableException constructor comment.
     * @param s java.lang.String
     */
    public NoProxyAvailableException(String s) {
	super(s);
    }
}
