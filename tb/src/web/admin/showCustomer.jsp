
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
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.customers.text" /></title>
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
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<p>
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.customers.text" /><br></span>
</p>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="backgroundcolor">
	<bean:size id="customersSize" name="customers" />
	<c:if test="${customersSize>10}">
		<c:if test="${employeeAuthorized}">
			<tr>
				<html:form action="/CreateCustomer">
					<td class="noBborderStyle" colspan="2"><html:submit
						styleId="button" titleKey="main.general.button.createcustomer.alttext.text">
						<bean:message key="main.general.button.createcustomer.text" />
					</html:submit></td>
				</html:form>
			</tr>
		</c:if>
	</c:if>
	<tr>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.customers.customername.text" />"><b><bean:message
			key="main.customer.shortname.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.customers.customername.text" />"><b><bean:message
			key="main.customer.name.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.customers.address.text" />"><b><bean:message
			key="main.customer.address.text" /></b></th>
		<c:if test="${employeeAuthorized}">
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.customers.edit.text" />"><b><bean:message
				key="main.customer.edit.text" /></b></th>
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.customers.delete.text" />"><b><bean:message
				key="main.customer.delete.text" /></b></th>
		</c:if>
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
		<td><c:out value="${customer.shortname}" /></td>
		<td><c:out value="${customer.name}" /></td>
		<td><c:out value="${customer.address}" /></td>

		<c:if test="${employeeAuthorized}">
			<td align="center"><html:link
				href="/tb/do/EditCustomer?cuId=${customer.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Customer" title="<bean:message key="main.headlinedescription.customers.edit.text"/>"/>
			</html:link></td>
			<html:form action="/DeleteCustomer">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${customer.id})"
					src="/tb/images/Delete.gif" alt="Delete Customer" titleKey="main.headlinedescription.customers.delete.text"/></td>
			</html:form>
		</c:if>
		</tr>
	</c:forEach>
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateCustomer">
				<td class="noBborderStyle" colspan="2"><html:submit
					styleId="button" titleKey="main.general.button.createcustomer.alttext.text">
					<bean:message key="main.general.button.createcustomer.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
</body>
</html:html>
