package se.bricole.xss.server;

/**
 * <p>The ClientEventListener interface is used for classes that wants to be informed of added and removed clients
 * in a ClientProxy.</p>
 * <p>The combination of this interface and the <i>ConnectionEventReceiver</i> interface is rather confusing. They
 * should be joined or named better.</p>
 * <p>Until further documented, this interface should not be used by module developers.</p>
 */
public interface SessionEventListener {

    public final static String vcId = "$Id: SessionEventListener.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";
    
    
    /**
     * Informs the listener that a client with the ClientSession object
     * <i>client</i> has been accepted by the server.
     *
     * @param	client	The new ClientConnection object.
     */
    public void clientStart(ClientSession client);	

    /**
     * Inform the listener that the client associated with the ClientConnection <i>client</i> has been removed
     * from the ClientProxy, for any reason.
     */
    public void clientStop(ClientSession c);
}
