package se.bricole.xss.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public interface IOFilterModule extends Module {
    public final static int TYPE = IOFILTER;

    public InputStream getInputFilter(InputStream is) throws IOException;
    public OutputStream getOutputFilter(OutputStream is) throws IOException;
}
