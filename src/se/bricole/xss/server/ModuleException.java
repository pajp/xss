package se.bricole.xss.server;

/**
 * An exception that can be thrown from a module or by the servers' module loader.
 */
public class ModuleException extends Exception {
    public final static String vcId = "$Id: ModuleException.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";

    Exception exception = null;
    public ModuleException() {
	super();
    }
    
    public ModuleException(String s) {
	super(s);
    }
    
    public ModuleException(Exception e) {
	this.exception = e;
    }
    
    public ModuleException(String s, Exception e) {
	super(s);
	this.exception = e;
    }
    
    public Exception getException() {
	return exception;
    }
	 
}
