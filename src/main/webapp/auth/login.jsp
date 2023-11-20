<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%
java.util.Enumeration<String> names = session.getAttributeNames();
while(names.hasMoreElements()) {
	String name = names.nextElement();
	session.removeAttribute(name);
}
%>
<html:html>
<head>
<title><bean:message key="main.general.title.text" /></title>
<jsp:include flush="true" page="/head-includes.jsp" />
</head>
<body>
<div align="right">
<font size="1pt">
	<c:out value="${buildProperties.version}" />
	<c:out value="${buildProperties.time}" />
	<c:out value="${gitProperties.branch}" />
	<c:out value="${gitProperties.shortCommitId}" />
</font>
</div>
<b>
<span style="color:red">
<html:errors />
</span>
</b>
Your user is not in my Database. Please ask your Administrator to create a Salat-User.
<div style="text-align: center">
	<h3>Try the new mobile friendly salad. Go to <a href="/chicoree/login.jsp" style="color: black">chicoree!</a></h3>
</div>
</body>
</html:html>
