<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html-el" %>

<html:html>
<head>
    <html:base />
</head>
<body>
<center>
    <h1 style="color: red;">
        <img width="65px" height="62px" src="images/fehlerteufel.jpg" title="<bean:message key="main.general.errormessage" />"/>
        ERROR
        <img width="65px" height="62px" src="images/fehlerteufer.jpg" title="<bean:message key="main.general.errormessage" />"/>
    </h1>
    <p><%= request.getAttribute("errorMessage") %></p>
</center>
</body>
</html:html>
