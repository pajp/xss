package se.bricole.xss.modules;

import se.bricole.xss.server.*;

public class ChatUser {
	
    private String nickname;
    private String realname;
    private String other;
    private ClientSession client;

    public String getNickname() {
	return nickname;
    }

    public String getRealname() {
	return realname;
    }

    public String getOther() {
	return other;
    }

    public ChatUser(String nickname,
		    String realname,
		    String other,
		    ClientSession client) {
	this.nickname = nickname;
	this.realname = realname;
	this.other    = other;	
	this.client   = client;
    }

    public ClientSession getClient() {
	return client;
    }
}






