package se.bricole.xss.modules;


class ChatException extends Exception {
	final static int ERRUNDEF   = 0;
	final static int NOSUCHNICK = 1;
	final static int NICKINUSE  = 2;
	final static int MUSTREGISTER = 3;

	int errno;

	public ChatException(int errno) {
	super();
	this.errno = errno;
	}

	public ChatException(int errno, String s) {
	super(s);
	this.errno = errno;
	}

	public ChatException(String s) {
	super(s);
	this.errno = 0;
	}

	public ChatException() {
	super();
	this.errno = 0;
	}

	public int getError() {
	return errno;
	}
}