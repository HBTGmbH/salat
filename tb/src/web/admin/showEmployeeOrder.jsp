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
<title><bean:message
	key="main.general.mainmenu.employeeorders.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployeeorder?eoId=" + id;
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
<h2><bean:message key="main.general.mainmenu.employeeorders.text" /></h2>
</p>
<br>
<span style="color:red"><html:errors footer="<br>"/>
</span>

<table class="center backgroundcolor">
	<tr>
		<th align="left"><b><bean:message
			key="main.employeeorder.employee.text" /></b>
		</td>
		<th align="left"><b><bean:message
			key="main.employeeorder.customerorder.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employeeorder.suborder.text" /></b></th>
		<!--  
		<td align="left"> <b><bean:message key="main.employeeorder.sign.text"/></b> </td>	
		-->
		<th align="left"><b><bean:message
			key="main.employeeorder.validfrom.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employeeorder.validuntil.text" /></b></th>
		<th align="center"><b><bean:message
			key="main.employeeorder.standingorder.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employeeorder.debithours.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employeeorder.status.text" /></b></th>
		<th align="center"><b><bean:message
			key="main.employeeorder.statusreport.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employeeorder.edit.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.employeeorder.delete.text" /></b></th>
	</tr>
	<c:forEach var="employeeorder" items="${employeeorders}"
		varStatus="statusID">
		<c:choose>
			<c:when test="${statusID.count%2==0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>
		<td><c:out
			value="${employeeorder.employeecontract.employee.name}" /></td>
		<td><c:out value="${employeeorder.suborder.customerorder.sign}" /></td>
		<td><c:out value="${employeeorder.suborder.sign}" /></td>
		<!-- 
      	<td><bean:write name="employeeorder" property="sign"/></td>
      	 -->
		<td><c:out value="${employeeorder.fromDate}" /></td>
		<td><c:out value="${employeeorder.untilDate}" /></td>
		<td align="center"><html:checkbox name="employeeorder"
			property="standingorder" disabled="true" /></td>
		<td><c:out value="${employeeorder.debithours}" /></td>
		<c:choose>
			<c:when test="${employeeorder.status==''}">
				<td>&nbsp;</td>
			</c:when>
			<c:otherwise>
				<td><c:out value="${employeeorder.status}" /></td>
			</c:otherwise>
		</c:choose>
		<td align="center"><html:checkbox name="employeeorder"
			property="statusreport" disabled="true" /></td>

		<logic:equal name="employeeAuthorized" value="true" scope="session">
			<td align="center"><html:link
				href="/tb/do/EditEmployeeorder?eoId=${employeeorder.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Employeeorder" />
			</html:link></td>
			<html:form action="/DeleteEmployeeorder">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${employeeorder.id})"
					src="/tb/images/Delete.gif" alt="Delete Employeeorder" /></td>
			</html:form>
		</logic:equal>
		</tr>
	</c:forEach>

	<tr>
		<html:form action="/CreateEmployeeorder">
			<td class="noBborderStyle" colspan="4"><html:submit styleId="button">
				<bean:message key="main.general.button.createemployeeorder.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
</body>
</html:html>
