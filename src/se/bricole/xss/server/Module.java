package se.bricole.xss.server;

import java.util.Properties;

/**
 * <p>
 * An XML module does not usually need to implement this interface,
 * since it is inherited by the XMLSessionTagHandler and XMLTagHandler
 * interfaces. It contains the constants Module.DUMMY, Module.SESSION,
 * Module.STATELESS and Module.XML which are used to construct the
 * TYPE constant in inheriting interfaces.
 * </p>
 * <p>
 * TODO:
 * The usage of the TYPE "constant" is pretty flawed from an OO
 * perspective, so it should at least be replaced with something
 * like getType().
 *
 * </p>
 */
public interface Module {
    public final static String vcId = "$Id: Module.java,v 1.4 2002/09/12 00:54:52 pipeman Exp $";

    public final static int DUMMY = 0;
    public final static int SESSION = 1;
    public final static int STATELESS = 2;
    public final static int XML = 4;
    public final static int AUTH = 8;
    public final static int WILDCARD = 16;
    public final static int FILTER = 32;
    public final static int REGISTRAR = 64;
    public final static int IOFILTER = 128;
    

    public void setProperties(Properties p) throws ModuleException;

}
