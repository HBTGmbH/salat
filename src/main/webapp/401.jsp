<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="html" uri="http://struts.apache.org/tags-html-el" %>
<%
    Cookie[] cookies = request.getCookies();
    for(Cookie killMyCookie : cookies) {
        killMyCookie.setMaxAge(0);
        killMyCookie.setPath("/");
        response.addCookie(killMyCookie);
    }
%>
<html:html>
<head>

</head>
<body>
<center>
    <p>Unauthenticated (401). Please restart SALAT.</p>
    <p><a href="/">>>Restart<<</a></p>
</center>
</body>
</html:html>
