package se.bricole.xss.server;

import java.io.IOException;

import org.w3c.dom.Document;

interface XMLClient {
    public String getProperty(String key);
    public void setProperty(String key, String value);

    public void send(Document d) throws IOException;
    public void sendAsynch(Document d) throws IOException;
}
