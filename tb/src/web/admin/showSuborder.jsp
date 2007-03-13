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
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.suborders.text" /></title>
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
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.suborders.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>"/>
</span>
<html:form action="/ShowSuborder">
	<html:text property="filter" size="40"/>
	<html:submit styleId="button" titleKey="main.general.button.filter.alttext.text">
		<bean:message key="main.general.button.filter.text" />
	</html:submit>
</html:form>

<table class="center backgroundcolor">
<bean:size id="subordersSize" name="suborders" />
<c:if test="${subordersSize>10}">

	<c:if test="${(employeeAuthorized && visibleOrdersPresent) || employeeIsResponsible}">
		<tr>
			<html:form action="/CreateSuborder">
				<td class="noBborderStyle" colspan="4"><html:submit styleId="button" titleKey="main.general.button.createsuborder.alttext.text">
					<bean:message key="main.general.button.createsuborder.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</c:if>

	<tr>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.customerorder.text" />"><b><bean:message
			key="main.suborder.customerorder.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.subordernumber.text" />"><b><bean:message
			key="main.suborder.sign.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.description.text" />"><b><bean:message
			key="main.suborder.shortdescription.short.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.description.text" />"><b><bean:message
			key="main.suborder.description.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.invoice.text" />"><b><bean:message
			key="main.suborder.invoice.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.currency.text" />"><b><bean:message
			key="main.suborder.currency.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.hourlyrate.text" />"><b><bean:message
			key="main.suborder.hourlyrate.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.edit.text" />"><b><bean:message
			key="main.suborder.edit.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.delete.text" />"><b><bean:message
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
		<td title="<c:out value="${suborder.customerorder.description}" />"><c:out value="${suborder.customerorder.sign}" /></td>
		<td><c:out value="${suborder.sign}" /></td>
		<td><c:out value="${suborder.shortdescription}" /></td>
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

		<c:choose>
			<c:when test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && (suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
				<td align="center">
					<html:link href="/tb/do/EditSuborder?soId=${suborder.id}">
						<img src="/tb/images/Edit.gif" alt="Edit Suborder" title="<bean:message key="main.headlinedescription.suborders.edit.text"/>"/>
					</html:link></td>
					<html:form action="/DeleteSuborder">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${suborder.id})"
					src="/tb/images/Delete.gif" alt="Delete Suborder" titleKey="main.headlinedescription.suborders.delete.text"/></td>
					</html:form>
			</c:when>
			<c:otherwise>
				<td align="center"><img height="12px" width="12px" src="/tb/images/verbot.gif"
					alt="Edit Suborder" title="<bean:message key="main.headlinedescription.suborders.accessdenied.text"/>"/></td>
				<td align="center"><img height="12px" width="12px" src="/tb/images/verbot.gif"
					alt="Delete Suborder" title="<bean:message key="main.headlinedescription.suborders.accessdenied.text"/>"/></td>
			</c:otherwise>
		</c:choose>
		</tr>
	</c:forEach>
	<c:if test="${(employeeAuthorized && visibleOrdersPresent) || employeeIsResponsible}">
		<tr>
			<html:form action="/CreateSuborder">
				<td class="noBborderStyle" colspan="4"><html:submit styleId="button" titleKey="main.general.button.createsuborder.alttext.text">
					<bean:message key="main.general.button.createsuborder.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
</body>
</html:html>
