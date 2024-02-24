<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib prefix="html" uri="http://struts.apache.org/tags-html-el" %>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<html:html>
<head>

</head>
<body>
<center>
    <h1 style="color: red;">
        <img width="65px" height="62px" src="<c:url value="/images/fehlerteufel.jpg"/>" title="<bean:message key="main.general.errormessage" />"/>
        ERROR
        <img width="65px" height="62px" src="<c:url value="/images/fehlerteufel.jpg"/>" title="<bean:message key="main.general.errormessage" />"/>
    </h1>
    <p><%= request.getAttribute("errorMessage") %></p>
    <p><a href="/">OK</a></p>
</center>
</body>
</html:html>
