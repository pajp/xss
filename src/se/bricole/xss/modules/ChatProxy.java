package se.bricole.xss.modules;

import java.io.*;
import java.util.*;

interface ChatProxy {
	public void unregisterUser(ChatUser user) throws ChatException;
	public void unregisterUser(String nickname) throws ChatException;
	public void registerUser(ChatUser user) throws ChatException;
	public ChatUser getUser(String nickname) throws ChatException;
	public Enumeration getUsers();

}