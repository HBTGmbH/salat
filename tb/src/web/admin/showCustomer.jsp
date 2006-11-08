
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
<title><bean:message key="main.general.mainmenu.customers.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteCustomer?cuId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>
<center>
<p>
<h2><bean:message key="main.general.mainmenu.customers.text" /></h2>
</p>
<br>
<br>

<span style="color:red"><html:errors /><br>
</span>

<table class="backgroundcolor">
	<tr>
		<th align="left"><b><bean:message
			key="main.customer.name.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customer.address.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customer.edit.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customer.delete.text" /></b></th>
	</tr>
	<c:forEach var="customer" items="${customers}" varStatus="statusID">
		<c:choose>
			<c:when test="${statusID.count%2==0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>
		<td><c:out value="${customer.name}" /></td>
		<td><c:out value="${customer.address}" /></td>

		<logic:equal name="employeeAuthorized" value="true" scope="session">
			<td align="center"><html:link
				href="/tb/do/EditCustomer?cuId=${customer.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Customer" />
			</html:link></td>
			<html:form action="/DeleteCustomer">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${customer.id})"
					src="/tb/images/Delete.gif" alt="Delete Customer" /></td>
			</html:form>
		</logic:equal>
		</tr>
	</c:forEach>
	<tr>
		<html:form action="/CreateCustomer">
			<td class="noBborderStyle" colspan="2"><html:submit>
				<bean:message key="main.general.button.createcustomer.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
<br>
<br>
<table>
	<tr>
		<html:form action="/ShowCustomer?task=back">
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
</center>
</body>
</html:html>
