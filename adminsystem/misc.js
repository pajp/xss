// misc administrative commands

// <shutdown/> - shuts down the server
// <list-connections/> - list all active connections
//                       in the current proxy

function myOnXML(session, proxy, element) {

  var name = element.getNodeName();
  if (name == "shutdown") {
    shutdown(session, proxy, element);
    return true;
  }
  if (name == "list-connections") {
    return listConnections(session, proxy, element);
  }
  return false;
}

function listConnections(session, proxy, element) {
  var doc = XMLUtil.newXML();
  var root = doc.createElement("client-list");
  doc.appendChild(root);

  var i=0;
  var clients = proxy.getClients();
  while (clients.hasNext()) {
    var client = clients.next();
    var clientNode = doc.createElement("client");
    clientNode.setAttribute("id", ""+client.getId());
    clientNode.setAttribute("ip", client.getInetAddress().getHostAddress());
    root.appendChild(clientNode);
    i++;
  }
  root.setAttribute("count", i);
  return doc;
}

function shutdown(session, proxy, element) {
  var doc = XMLUtil.newXML();
  var e = doc.createElement("shutdown-ack");
  doc.appendChild(e);
  session.send(doc);
  proxy.getConfiguration().shutdown();
}


this.tags = new Array();
this.tags[0] = "shutdown";
this.tags[1] = "list-connections";
this.onXML = myOnXML;
