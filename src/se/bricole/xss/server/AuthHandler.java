package se.bricole.xss.server;

import org.w3c.dom.Element;
import java.io.IOException;

/**
 * A regular AuthHandler (also called "Authenticator" elsewhere in the
 * documentation). It is an "active" authenticator in that it takes care
 * of comparing the password itself. By contrast, a passive authenticator
 * (AuthHandlerPassive) hands over the password to the XSS host, letting
 * XSS do the actual authentication.
 *
 */
public interface AuthHandler extends Module {
    public final static String vcId = "$Id: AuthHandler.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";

    public String[] getAuthDomains();
    static int TYPE = AUTH;

    public boolean authenticate(ClientSession client, ClientProxy proxy,
				String domain, String userName, char[] password)
	throws IOException;
}
