// Sample "hello world" javascript for XSS.


function test(session, proxy, element) {
  var myXML = XMLUtil.newXML();
  var element = myXML.createElement("message");
  var count = proxy.getSharedObject("count");
  if (!count) {
    count = 0;
  }
  count++;
  proxy.addSharedObject("count", count);
  element.setAttribute("text", "hello, world " + count);
  myXML.appendChild(element);
  return myXML;
}

this.tag = "hello";
this.onXML = test;
