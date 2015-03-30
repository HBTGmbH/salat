<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>

<html>
<center><h1 style="color: red;"><img width="65px" height="62px" src="/tb/images/fehlerteufel.jpg" title="<bean:message key="main.general.errormessage" />" /> ERROR <img width="65px" height="62px" src="/tb/images/fehlerteufer.jpg" title="<bean:message key="main.general.errormessage" />" /></h1>
<p><%= request.getAttribute("errorMessage") %></p>
</center></html>