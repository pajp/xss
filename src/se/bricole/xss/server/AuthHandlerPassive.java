package se.bricole.xss.server;

import java.io.IOException;

/**
 * <p>This passive AuthHandler also specifies a method of retreiving the
 * password for a specific user, letting the server handle the 
 * authentication process (thus allowing use of MD5 authentication 
 * implemented in XSS). A passive AuthHandler must also be able 
 * to perform active authentication through the regular AuthHandler
 * interface.</p>
 *
 * <p>The MD5 challenge response works this way:<br>
 * <ol><li> Client sends <code>&lt;auth init-challenge="" domain="mydomain"/&gt;</code>
 *          where "mydomain" is the name of the security domain which the client wants to 
 *          authenticate to.
 *     <li> Server responds with <code>&lt;auth-challenge domain="mydomain" challenge="XXXX" /&gt;</code>,
 *          where XXXX is an arbitrary random string ("the challenge").
 *     <li> The client concatenates the challenge with the user's password (the order being
 *          password + challenge) and computes an MD5 checksum of the resulting string.
 *     <li> The client sends the MD5 checksum in hex-coded format to the server:
 *          <code>&lt;auth challenge-response="4C437C32..." domain="mydomain" /&gt;</code>
 *     <li> The server does matches the supplied MD5 with its own computation, and returns
 *          <code>&lt;auth-ok message="..."
 *          domain="mydomain"&gt;</code> if the authentication was
 *          successful, <code>&lt;auth-error message="..."
 *          domain="mydomain"&gt;</code> if it wasn't.
 * </ol>
 * There is a Flash implementation of the MD5 algorithm available
 * <a href="http://flashexperiments.insh-allah.com/#MD5">here</a>, but
 * it's GPL so I'm not sure if I can include it with XSS.
 *</p> 

 *
 * @see se.bricole.xss.server.AuthHandler
 */
public interface AuthHandlerPassive extends AuthHandler {

    public char[] getPassword(ClientSession client, ClientProxy proxy,
			      String domain, String username)
	throws IOException;
}
