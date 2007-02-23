<%@ page import="org.tb.bdom.Employee" %>

<%@ taglib
	uri="http://java.sun.com/jsp/jstl/core"
	prefix="c"%>
<%@ taglib
	uri="http://struts.apache.org/tags-html"
	prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
	
<%
	Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 
%>

<html>
<head>
<meta
	http-equiv="Content-Type"
	content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.title.text" /></title>
<link
	rel="stylesheet"
	type="text/css"
	href="/tb/tb.css" />
</head>
<body>
<center>
<h2 style="color: black"><bean:message key="main.general.mainmenu.welcome.text" /><br></span>
</center>
<br>
<p><bean:message key="main.general.mainmenu.logininfo.text" /> <b>'<%=loginEmployee.getFirstname()%> <%=loginEmployee.getLastname()%>'</b> (<%=loginEmployee.getLoginname()%>) [<%=loginEmployee.getStatus()%>]</p>

<menu>
  <li><html:link action="/ShowDailyReport"><bean:message key="main.general.mainmenu.daily.text"/> </html:link> </li><br>
  <li><html:link action="/ShowMonthlyReport"><bean:message key="main.general.mainmenu.monthly.text"/> </html:link> </li><br>
</menu>
<br>
<c:if test="${loginEmployee.status != 'ma'}">
  	<b><bean:message key="main.general.mainmenu.admin.text" />:</b>
  	<br>
	<menu>
    	<li><html:link action="/ShowEmployee"><bean:message key="main.general.mainmenu.employees.text"/></html:link></li><br>
 	 	<li><html:link action="/ShowEmployeecontract"><bean:message key="main.general.mainmenu.employeecontracts.text"/></html:link></li><br>
 	 	<li><html:link action="/ShowEmployeeorder"><bean:message key="main.general.mainmenu.employeeorders.text"/></html:link></li><br>	 
  	 	<li><html:link action="/ShowCustomer"><bean:message key="main.general.mainmenu.customers.text"/></html:link></li><br>
 	 	<li><html:link action="/ShowCustomerorder"><bean:message key="main.general.mainmenu.customerorders.text"/></html:link></li><br>
 	 	<li><html:link action="/ShowSuborder"><bean:message key="main.general.mainmenu.suborders.text"/></html:link></li><br>
	</menu>
</c:if>

<html:form action="/LogoutEmployee">
   <html:submit>
        <bean:message key="main.general.logout.text"/>
   </html:submit>
</html:form>
<!-- 
<form name="logoutform" method="post" action="/tb/do/LogoutEmployee">
   <input type="submit" name="Submit" value="Logout">
</form>
 -->
 
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>

</body>
</html>