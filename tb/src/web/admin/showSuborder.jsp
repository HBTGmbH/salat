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
<title><bean:message key="main.general.mainmenu.suborders.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteSuborder?soId=" + id;
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
<h2><bean:message key="main.general.mainmenu.suborders.text" /></h2>
</p>
<br>
<span style="color:red"><html:errors footer="<br>"/>
</span>

<table class="center backgroundcolor">
	<tr>
		<th align="left"><b><bean:message
			key="main.suborder.customerorder.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.sign.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.description.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.invoice.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.currency.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.hourlyrate.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.edit.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.suborder.delete.text" /></b></th>
	</tr>

	<c:forEach var="suborder" items="${suborders}" varStatus="statusID">
		<c:choose>
			<c:when test="${statusID.count%2==0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>
		<td><c:out value="${suborder.customerorder.sign}" /></td>
		<td><c:out value="${suborder.sign}" /></td>
		<td><c:out value="${suborder.description}" /></td>
		<td align="center"><logic:equal name="suborder"
			property="invoice" value="Y">
			<bean:message key="main.suborder.invoice.yes.text" />
		</logic:equal> <logic:equal name="suborder" property="invoice" value="N">
			<bean:message key="main.suborder.invoice.no.text" />
		</logic:equal> <logic:equal name="suborder" property="invoice" value="U">
			<bean:message key="main.suborder.invoice.undefined.text" />
		</logic:equal></td>
		<td><c:out value="${suborder.currency}" /></td>
		<td><c:out value="${suborder.hourly_rate}" /></td>

		<logic:equal name="employeeAuthorized" value="true" scope="session">
			<td align="center"><html:link
				href="/tb/do/EditSuborder?soId=${suborder.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Suborder" />
			</html:link></td>
			<html:form action="/DeleteSuborder">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${suborder.id})"
					src="/tb/images/Delete.gif" alt="Delete Suborder" /></td>
			</html:form>
		</logic:equal>
		</tr>
	</c:forEach>
	<tr>
		<html:form action="/CreateSuborder">
			<td class="noBborderStyle" colspan="4"><html:submit styleId="button">
				<bean:message key="main.general.button.createsuborder.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
</body>
</html:html>
