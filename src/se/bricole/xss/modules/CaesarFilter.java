package se.bricole.xss.modules;

import se.bricole.xss.server.*;

import java.io.*;
import java.util.*;

/**
 *
 */
public class CaesarFilter implements IOFilterModule {
    int roll = 13;
    boolean inbound = true, outbound = true;

    public InputStream getInputFilter(InputStream is)
    throws IOException {
	if (inbound) return new CaesarInputStream(roll, is);
	return is;
    }

    public OutputStream getOutputFilter(OutputStream os)
    throws IOException {
	if (outbound) return new CaesarOutputStream(roll, os);
	return os;
    }

    public void setProperties(Properties p)
    throws ModuleException {
	String direction = p.getProperty("direction");
	if (direction == null) direction = "both";
	direction = direction.toLowerCase();
	if ("inbound".equals(direction)) {
	    inbound = true;
	    outbound = false;
	}
	if ("outbound".equals(direction)) {
	    inbound = false;
	    outbound = true;
	}
    }

    static class CaesarInputStream extends InputStream {
	InputStream is;
	int roll;
	public CaesarInputStream(int roll, InputStream is) {
	    Server.debug("attached " + is + " to " + toString());
	    this.is = is;
	    this.roll = roll;
	}

	public int read()
	throws IOException {
	    byte data = (byte) is.read();
	    if (data != 0 && data != -1) data -= roll;
	    return data;
	}

	public int read(byte[] b, int off, int len)
	throws IOException {
	    int num = is.read(b, off, len);
	    if (num == -1) return -1;
	    for (int i=off; i < off+len; i++) {
		if (b[i] != 0) b[i] -= roll;
	    }
	    return num;
	}

    }

    static class CaesarOutputStream extends OutputStream {
	OutputStream os;
	int roll;
	public CaesarOutputStream(int roll, OutputStream os) {
	    this.os = os;
	    this.roll = roll;
	}

	public void write(int b) 
	throws IOException {
	    byte data = (byte) b;
	    data += roll;
	    os.write(data);
	}

	public void write(byte[] b, int off, int len)
	throws IOException {
	    byte[] temp = new byte[b.length];
	    System.arraycopy(b, 0, temp, 0, b.length);
	    for (int i=off; i < off+len; i++) {
		if (temp[i] != 0) temp[i] += roll;
	    }
	    os.write(temp, off, len);
	}
    }
}
