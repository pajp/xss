package com.example;

import se.bricole.xss.server.ClientProxy;
import org.w3c.dom.Element;

import java.util.Properties;

import se.bricole.xss.server.XMLUtil;
import se.bricole.xss.server.ClientSession;

public class SampleCounterModule implements se.bricole.xss.server.Module, 
					    se.bricole.xss.server.XMLTagHandler {

	private String[] tags = {"counter"};
	private static int counter = 0;
	
	public SampleCounterModule() { super(); }

	public java.lang.String[] getTagNames() { return tags; }

	public boolean xmlTag(ClientSession client, ClientProxy proxy, Element element) 
		throws se.bricole.xss.server.ParserException, 
		       java.io.IOException {

		counter++;
		Properties p = new Properties();
		p.setProperty("count", "" + counter);
		client.send(XMLUtil.simpleTag("counter-reply", p));
		proxy.serverBroadcast(client, XMLUtil.simpleTag("counter-update", p), true);
		return true;
	}

    public void setProperties(Properties p) {
	// this module does not support properties
    }
}
