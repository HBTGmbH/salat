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
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.employees.text" /></title>
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
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<p>
<h2><bean:message key="main.general.mainmenu.employees.text" /></h2>
</p>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="center backgroundcolor">
<bean:size id="employeesSize" name="employees" />
<c:if test="${employeesSize>10}">
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateEmployee">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createemployee.alttext.text">
					<bean:message key="main.general.button.createemployee.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
	</c:if>
	<tr>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.fristname.text" />"><b><bean:message
			key="main.employee.firstname.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.lastname.text" />"><b><bean:message
			key="main.employee.lastname.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.sign.text" />"><b><bean:message
			key="main.employee.sign.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.loginname.text" />"><b><bean:message
			key="main.employee.loginname.text" /></b></th>
		<!--  
		<td align="left"> <b><bean:message key="main.employee.password.text"/></b> </th>
		-->
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.status.text" />"><b><bean:message
			key="main.employee.status.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.gender.text" />"><b><bean:message
			key="main.employee.gender.text" /></b></th>
		<c:if test="${employeeAuthorized}">
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.employees.edit.text" />"><b><bean:message
				key="main.employee.edit.text" /></b></th>
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.employees.delete.text" />"><b><bean:message
				key="main.employee.delete.text" /></b></th>
		</c:if>
	</tr>

	<c:forEach var="employee" items="${employees}" varStatus="statusID">
		<c:if test="${employee.lastname!='admin' || loginEmployee.status eq 'adm'}">
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
			<c:if test="${employeeAuthorized}">
				<td align="center"><html:link
					href="/tb/do/EditEmployee?emId=${employee.id}">
					<img src="/tb/images/Edit.gif" alt="Edit Employee" title="<bean:message key="main.headlinedescription.employees.edit.text"/>"/>
				</html:link></td>
				<html:form action="/DeleteEmployee">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employee.id})"
						src="/tb/images/Delete.gif" alt="Delete Employee" titleKey="main.headlinedescription.employees.delete.text"/></td>
				</html:form>
			</c:if>
			</tr>
		</c:if>
	</c:forEach>
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateEmployee">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createemployee.alttext.text">
					<bean:message key="main.general.button.createemployee.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>

</table>
</body>
</html:html>
