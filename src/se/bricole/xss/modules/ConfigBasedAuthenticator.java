package se.bricole.xss.modules;

import se.bricole.xss.server.*;
import java.util.*;

/**
 * A simple Authenticator that uses the configuration properties to
 * determine username, password and domain data.
 *
 * Module properties:
 * "domains"          comma-separated list of authentication domains
 * "password.<user>"  password for user <user>
 *
 */
public class ConfigBasedAuthenticator implements AuthHandlerPassive {

    Properties p = null;

    String[] domains;
    public void setProperties(Properties p) throws ModuleException {
	this.p = p;
	init();
    }

    private void init() throws ModuleException {
	List domainsList = new LinkedList();
	String domainListString = p.getProperty("domains");
	if (domainListString == null) {
	    throw new ModuleException("missing \"domains\" property");
	}
	StringTokenizer tokenizer = new StringTokenizer(domainListString,
							",");
	while (tokenizer.hasMoreTokens()) {
	    domainsList.add(tokenizer.nextToken());
	}

	domains = new String[domainsList.size()];
	
	Iterator i = domainsList.iterator();
	int j=0;
	while (i.hasNext()) {
	    domains[j] = (String) i.next();
	    j++;
	}
    }

    public String[] getAuthDomains() {
	return domains;
    }

    public boolean authenticate(ClientSession client, ClientProxy proxy,
				String domain, String userName, char[] password) {
	String passwordProp = p.getProperty("password." + userName);
	if (passwordProp == null) return false;

	if (passwordProp.equals(new String(password))) return true;

	return false;
    }

    public char[] getPassword(ClientSession client, ClientProxy proxy,
			      String domain, String username) {
	String passwordProp = p.getProperty("password." + username);
	if (passwordProp == null) return null;

	char[] chars = new char[passwordProp.length()];
	
	passwordProp.getChars(0, passwordProp.length(), chars, 0);
	return chars;

    }
    
}
