package se.bricole.xss.server;

import java.io.IOException;
import org.w3c.dom.Element;
/**
 * <p>This interface should be implemented by all <b>Stateful</b> XML modules (a.k.a. "Session Modules").
 * The session state is received through the constructor ClientProxy and ClientConnection parameters and
 * should me maintained during the objects' entire lifetime.</p>
 * <p>XMLSessionTagHandler defines the constant TYPE as Module.XML|Module.SESSION.</p>
 */
public interface XMLSessionTagHandler extends XMLModule {

    public final static String vcId = "$Id: XMLSessionTagHandler.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";

    public void init(ClientProxy p, ClientSession c);


    /**
     * <p>This is the method that is called when the XML parser has finished and found that the root element (tag)
     * matches one of the names returned by getTagNames().</p>
     *
     * <p>This method should throw a ParserException if anything goes
     * wrong with the interpretation of the data, or an
     * IOException if there are any I/O-related problems. Throwing any
     * of these will cause the server to terminate and
     * dereference the client.</p>
     *
     * @param	e	The root element ("firstChild") of the XML document received by the parser.
     */
    public boolean xmlTag(Element e) throws ParserException, IOException;
    
    /**
     * This method should return an array of String containing the names of the tags for which it wants to be
     * called. This method is only called once upon module initialization.
     */
    public String[] getTagNames();
    public static int TYPE = XML | SESSION;
}
