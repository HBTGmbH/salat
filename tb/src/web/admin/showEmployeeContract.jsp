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
<title><bean:message key="main.general.application.title" /> - <bean:message
	key="main.general.mainmenu.employeecontracts.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployeecontract?ecId=" + id;
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
<h2><bean:message key="main.general.mainmenu.employeecontracts.text" /></h2>
</p>
<br>
<span style="color:red"><html:errors footer="<br>"/>
</span>

<table class="center backgroundcolor">
	<tr>
		<th align="left" title="<bean:message key="main.headlinedescription.employeecontracts.employeename.text" />"><b><bean:message
			key="main.employeecontract.employee.text" /></b></th>
		<th align="left" title="<bean:message key="main.headlinedescription.employeecontracts.taskdescription.text" />"><b><bean:message
			key="main.employeecontract.taskdescription.text" /></b></th>
		<th align="left" title="<bean:message key="main.headlinedescription.employeecontracts.validfrom.text" />"><b><bean:message
			key="main.employeecontract.validfrom.text" /></b></th>
		<th align="left" title="<bean:message key="main.headlinedescription.employeecontracts.validuntil.text" />"><b><bean:message
			key="main.employeecontract.validuntil.text" /></b></th>
		<th align="center" title="<bean:message key="main.headlinedescription.employeecontracts.freelancer.text" />"><b><bean:message
			key="main.employeecontract.freelancer.text" /></b></th>
		<th align="center" title="<bean:message key="main.headlinedescription.employeecontracts.dailyworkingtime.text" />"><b><bean:message
			key="main.employeecontract.dailyworkingtime.text" /></b></th>
		<th align="center" title="<bean:message key="main.headlinedescription.employeecontracts.vacationdaysperyear.text" />"><b><bean:message
			key="main.employeecontract.yearlyvacation.text" /></b></th>
		<c:if test="${employeeAuthorized}">
			<th align="left" title="<bean:message key="main.headlinedescription.employeecontracts.edit.text" />"><b><bean:message
				key="main.employeecontract.edit.text" /></b></th>
			<th align="left" title="<bean:message key="main.headlinedescription.employeecontracts.delete.text" />"><b><bean:message
				key="main.employeecontract.delete.text" /></b></th>
		</c:if>
	</tr>

	<c:forEach var="employeecontract" items="${employeecontracts}" varStatus="statusID">
		<c:if test="${employeecontract.employee.lastname!='admin' || loginEmployee.status eq 'adm'}">
			<c:choose>
				<c:when test="${statusID.count%2==0}">
					<tr class="primarycolor">
				</c:when>
				<c:otherwise>
					<tr class="secondarycolor">
				</c:otherwise>
			</c:choose>
			<td><c:out value="${employeecontract.employee.name}" /></td>
			<td><c:out value="${employeecontract.taskDescription}" /></td>
			<td><c:out value="${employeecontract.validFrom}" /></td>
			<td><c:out value="${employeecontract.validUntil}" /></td>
			<td align="center"><html:checkbox name="employeecontract"
				property="freelancer" disabled="true" /></td>
			<td align="center"><c:out
				value="${employeecontract.dailyWorkingTime}" /></td>
			<td align="center"><c:out
				value="${employeecontract.vacationEntitlement}" /></td>

			<c:if test="${employeeAuthorized}">
				<td align="center"><html:link
					href="/tb/do/EditEmployeecontract?ecId=${employeecontract.id}">
					<img src="/tb/images/Edit.gif" alt="Edit Employeecontract" />
				</html:link></td>
				<html:form action="/DeleteEmployeecontract">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employeecontract.id})"
						src="/tb/images/Delete.gif" alt="Delete Employeecontract" /></td>
				</html:form>
			</c:if>
			</tr>
		</c:if>
	</c:forEach>
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateEmployeecontract">
				<td class="noBborderStyle" colspan="4"><html:submit styleId="button">
					<bean:message key="main.general.button.createemployeecontract.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
</body>
</html:html>
