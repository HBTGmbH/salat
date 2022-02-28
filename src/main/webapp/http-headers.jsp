<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="java.util.Enumeration" %>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<html:html>
<head>

</head>
<body>
<c:if test="${'34324-65634-423423-323232' == param.secret}">
	<h1>HTTP Headers</h1>
	<ul>
	<%
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			out.print("<li>");
			String headerName = headerNames.nextElement();
			out.print(headerName);
			out.print(": ");
			Enumeration<String> values = request.getHeaders(headerName);
			while (values.hasMoreElements()) {
				out.print(values.nextElement());
				out.print(", ");
			}
			out.print("</li>");
		}
	%>
	</ul>
	<h1>Servlet data</h1>
	<ul>
		<li>Request URI: <%= request.getRequestURI() %></li>
		<li>Request URL: <%= request.getRequestURL() %></li>
		<li>Context path: <%= request.getContextPath() %></li>
		<li>Remote host: <%= request.getRemoteHost() %></li>
		<li>Remote Port: <%= request.getRemotePort() %></li>
		<li>Scheme: <%= request.getScheme() %></li>
		<li>encodeRedirectURL(test-url.html): <%= response.encodeRedirectURL("test-url.html") %></li>
		<li>encodeURL(test-url.html): <%= response.encodeURL("test-url.html") %></li>
	</ul>
</c:if>
</body>
</html:html>
