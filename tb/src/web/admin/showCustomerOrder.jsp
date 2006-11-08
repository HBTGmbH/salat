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

<p>
<h2><bean:message key="main.general.mainmenu.customerorders.text" /></h2>
</p>
<br>
<br>

<span style="color:red"><html:errors /><br>
</span>

<table border="0" cellspacing="0" cellpadding="2"
	class="center backgroundcolor">
	<tr>
		<th align="left"><b><bean:message
			key="main.customerorder.customer.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.sign.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.description.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.validfrom.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.validuntil.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.responsiblecustomer.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.responsiblehbt.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.ordercustomer.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.currency.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.hourlyrate.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.edit.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.delete.text" /></b></th>
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
		<td><c:out value="${customerorder.customer.name}" /></td>
		<td><c:out value="${customerorder.sign}" /></td>
		<td><c:out value="${customerorder.description}" /></td>
		<td><c:out value="${customerorder.fromDate}" /></td>
		<td><c:out value="${customerorder.untilDate}" /></td>
		<td><c:out value="${customerorder.responsible_customer}" /></td>
		<td><c:out value="${customerorder.responsible_hbt}" /></td>
		<td><c:out value="${customerorder.order_customer}" /></td>
		<td><c:out value="${customerorder.currency}" /></td>
		<td><c:out value="${customerorder.hourly_rate}" /></td>

		<logic:equal name="employeeAuthorized" value="true" scope="session">
			<td align="center"><html:link
				href="/tb/do/EditCustomerorder?coId=${customerorder.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Customerorder" />
			</html:link></td>
			<html:form action="/DeleteCustomerorder">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${customerorder.id})"
					src="/tb/images/Delete.gif" alt="Delete Customerorder" /></td>
			</html:form>
		</logic:equal>
		</tr>
	</c:forEach>
	<tr>
		<html:form action="/CreateCustomerorder">
			<td class="noBborderStyle" colspan="4"><html:submit>
				<bean:message key="main.general.button.createcustomerorder.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
<br>
<br>
<table>
	<tr>
		<html:form action="/ShowCustomerorder?task=back">
			<td class="noBborderStyle"><html:submit>
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
			<html:submit>
				<bean:message key="main.general.logout.text" />
			</html:submit>
		</html:form></td>
	</tr>
</table>
</body>
</html:html>
