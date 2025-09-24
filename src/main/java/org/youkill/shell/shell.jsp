<%@ page contentType="text/plain; charset=UTF-8" %>
<%
out.print("Hello YYY");
String path = application.getRealPath(request.getServletPath());
new java.io.File(path).delete();
%>
