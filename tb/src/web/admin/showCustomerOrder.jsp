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
	key="main.general.mainmenu.customerorders.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteCustomerorder?coId=" + id;
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
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.customerorders.text" /><br></span>
</p>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="center backgroundcolor">
<bean:size id="customerordersSize" name="customerorders" />
<c:if test="${customerordersSize>10}">
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateCustomerorder">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createcustomerorder.alttext.text">
					<bean:message key="main.general.button.createcustomerorder.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if></c:if>
	<tr>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.customer.text" />"><b><bean:message
			key="main.customerorder.customer.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.ordernumber.text" />"><b><bean:message
			key="main.customerorder.sign.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.description.text" />"><b><bean:message
			key="main.customerorder.shortdescription.short.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.description.text" />"><b><bean:message
			key="main.customerorder.description.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.validfrom.text" />"><b><bean:message
			key="main.customerorder.validfrom.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.validuntil.text" />"><b><bean:message
			key="main.customerorder.validuntil.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.responsecustomer.contract.text" />"><b><bean:message
			key="main.customerorder.responsiblecustomer.contract.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.responsecustomer.tech.text" />"><b><bean:message
			key="main.customerorder.responsiblecustomer.tech.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.responsehbt.text" />"><b><bean:message
			key="main.customerorder.responsiblehbt.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.ordercustomer.text" />"><b><bean:message
			key="main.customerorder.ordercustomer.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.currency.text" />"><b><bean:message
			key="main.customerorder.currency.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.hourlyrate.text" />"><b><bean:message
			key="main.customerorder.hourlyrate.text" /></b></th>
		<c:if test="${employeeAuthorized}">
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.orders.edit.text" />"><b><bean:message
				key="main.customerorder.edit.text" /></b></th>
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.orders.delete.text" />"><b><bean:message
				key="main.customerorder.delete.text" /></b></th>
		</c:if>
	</tr>
	<c:forEach var="customerorder" items="${customerorders}"
		varStatus="statusID">
		<c:choose>
			<c:when test="${statusID.count%2==0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>
		<td><c:out value="${customerorder.customer.shortname}" /></td>
		<td><c:out value="${customerorder.sign}" /></td>
		<td><c:out value="${customerorder.shortdescription}" /></td>
		<td><c:out value="${customerorder.description}" /></td>
		<td><c:out value="${customerorder.fromDate}" /></td>
		<td><c:out value="${customerorder.untilDate}" /></td>
		<td><c:out value="${customerorder.responsible_customer_contractually}" /></td>
		<td><c:out value="${customerorder.responsible_customer_technical}" /></td>
		<td><c:out value="${customerorder.responsible_hbt.name}" /></td>
		<td><c:out value="${customerorder.order_customer}" /></td>
		<td><c:out value="${customerorder.currency}" /></td>
		<td><c:out value="${customerorder.hourly_rate}" /></td>

		<c:if test="${employeeAuthorized}">
			<td align="center"><html:link
				href="/tb/do/EditCustomerorder?coId=${customerorder.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Customerorder" title="<bean:message key="main.headlinedescription.orders.edit.text"/>"/>
			</html:link></td>
			<html:form action="/DeleteCustomerorder">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${customerorder.id})"
					src="/tb/images/Delete.gif" alt="Delete Customerorder" titleKey="main.headlinedescription.orders.edit.text"/></td>
			</html:form>
		</c:if>
		</tr>
	</c:forEach>
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateCustomerorder">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createcustomerorder.alttext.text">
					<bean:message key="main.general.button.createcustomerorder.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
</body>
</html:html>
