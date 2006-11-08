<%@ page import="org.tb.bdom.Employee"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%

%>

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.mainmenu.employees.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployee?emId=" + id;
			form.submit();
		}
	}					
 
</script>


</head>
<body>
<center>
<p>
<h2><bean:message key="main.general.mainmenu.employees.text" /></h2>
</p>
<br>
<br>

<span style="color:red"><html:errors /><br>
</span>

<table class="center backgroundcolor">
	<tr>
		<th align="left"><b><bean:message
			key="main.employee.firstname.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employee.lastname.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employee.sign.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employee.loginname.text" /></b></th>
		<!--  
		<td align="left"> <b><bean:message key="main.employee.password.text"/></b> </th>
		-->
		<th align="left"><b><bean:message
			key="main.employee.status.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employee.gender.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employee.edit.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employee.delete.text" /></b></th>
	</tr>

	<c:forEach var="employee" items="${employees}" varStatus="statusID">
		<c:if test="${employee.lastname!='admin'}">
			<c:choose>
				<c:when test="${statusID.count%2==0}">
					<tr class="primarycolor">
				</c:when>
				<c:otherwise>
					<tr class="secondarycolor">
				</c:otherwise>
			</c:choose>
			<td><c:out value="${employee.firstname}" /></td>
			<td><c:out value="${employee.lastname}" /></td>
			<td><c:out value="${employee.sign}" /></td>
			<td><c:out value="${employee.loginname}" /></td>
			<!--  
      	<td><bean:write name="employee" property="password"/></td>
      	-->
			<td><c:out value="${employee.status}" /></td>
			<td align="center"><c:out value="${employee.gender}" /></td>

			<logic:equal name="employeeAuthorized" value="true" scope="session">
				<td align="center"><html:link
					href="/tb/do/EditEmployee?emId=${employee.id}">
					<img src="/tb/images/Edit.gif" alt="Edit Employee" />
				</html:link></td>
				<html:form action="/DeleteEmployee">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employee.id})"
						src="/tb/images/Delete.gif" alt="Delete Employee" /></td>
				</html:form>
			</logic:equal>
			</tr>
		</c:if>
	</c:forEach>
	<tr>
		<html:form action="/CreateEmployee">
			<td class="noBborderStyle" colspan="4"><html:submit
				styleId="button">
				<bean:message key="main.general.button.createemployee.text" />
			</html:submit></td>
		</html:form>
	</tr>

</table>
<br>
<br>
<table>
	<tr>
		<html:form action="/ShowEmployee?task=back">
			<td class="noBborderStyle"><html:submit styleId="button">
				<bean:message key="main.general.button.backmainmenu.text" />
			</html:submit></td>
		</html:form>
	</tr>

</table>

<br>
<br>
<table class="center">
	<tr>
		<td class="noBborderStyle"><html:form action="/LogoutEmployee">
			<html:submit styleId="button">
				<bean:message key="main.general.logout.text" />
			</html:submit>
		</html:form></td>
	</tr>
</table>
</center>
</body>
</html:html>
