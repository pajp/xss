package se.bricole.xss.server;

import org.w3c.dom.Element;
import java.io.IOException;

/**
 * This interface should be implemented by stateless modules. No state of a particular client is
 * assumed to be stored in an XMLTagHandler instance. As of now, the life cycle of an
 * XMLTagHandler is unspecified - the server may use a single instance to serve multiple
 * simultaneous requests, for example.
 */
public interface XMLTagHandler extends XMLModule {

    public final static String vcId = "$Id: XMLTagHandler.java,v 1.3 2002/09/12 00:00:34 pipeman Exp $";

    /**
     * This method should return an array of String containing the
     * names of the tags for which it wants to be called. This method
     * is only called once upon module initialization.
     */
    public String[] getTagNames();
    static int TYPE = XML | STATELESS;
    
    /**
     * <p>This is the method that is called when the XML parser has finished
     * and found that the root element (tag) matches one of the names returned
     * by getTagNames() or otherwise registered.</p>
     *
     * <p>This method should throw a ParserException if anything goes wrong with the
     * interpretation of the data, or an IOException if there are any I/O-related problems.
     * Throwing any of these will cause the server to terminate and
     * dereference the client. Thus is general, you don't have to deal with I/O problems 
     * related to the network, since that means the connection is gone and you should terminate
     * anyway. You might want to use try {..} finally {..}-claues for cleanup in your xmlTag()
     * method, though.</p>
     *
     * @param	client	The ClientConnection from which this request originated
     * @param	proxy	The ClientProxy to which the request's ClientConnection belong
     * @param	e	The root element ("firstChild") of the XML document received by the parser.
     */
    public boolean xmlTag(ClientSession client,
			  ClientProxy proxy,
			  Element element)
	throws ParserException, IOException, ModuleException;
}


