package se.bricole.xss.server;

/**
 * Specifies administrative duties such as stopping the server.
 *
 */
interface ServerManager {
    public void shutdown(boolean exitVM);
    public void restart();
    public long getUptime();
}
