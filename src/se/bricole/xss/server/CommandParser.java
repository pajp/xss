package se.bricole.xss.server;

import java.io.IOException;

private interface CommandParser {
    public final static String vcId = "$Id: CommandParser.java,v 1.2 2002/09/12 00:00:34 pipeman Exp $";

    public boolean parse(StringBuffer buff)
	throws IOException, ParserException;

}
