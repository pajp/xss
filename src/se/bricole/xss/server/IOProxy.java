package se.bricole.xss.server;

import java.util.HashMap;

/**
 * Creation date: (2001-06-10 07:18:40)
 * @author: Rasmus Sten
 */
public class IOProxy {
	HashMap clientReaders = new HashMap();
	HashMap clientWriters = new HashMap();
	/**
	 * IOProxy constructor.
	 */
	public IOProxy() {
		super();
	}
}
