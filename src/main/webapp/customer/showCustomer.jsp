<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<html:html>
<head>

<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.customers.text" /></title>
<link rel="stylesheet" type="text/css" href="/style/tb.css" />
<link rel="shortcut icon" type="image/x-icon" href="/favicon.ico" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/do/DeleteCustomer?cuId=" + id;
			form.submit();
		}
	}
	
	function refresh(form) {	
		form.action = "/do/ShowCustomer?task=refresh";
		form.submit();
	}
						
  	function showWMTT(Trigger,id) {
  	  wmtt = document.getElementById(id);
    	var hint;
   	 hint = Trigger.getAttribute("hint");
   	 //if((hint != null) && (hint != "")){
   	 	//wmtt.innerHTML = hint;
    	wmtt.style.display = "block";
   	 //}
	}

	function hideWMTT() {
		wmtt.style.display = "none";
	}
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.customers.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="backgroundcolor">
<html:form action="/ShowCustomer?task=refresh">
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.filter.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left">
			<html:text property="filter" size="40" />
			<html:submit styleId="button" titleKey="main.general.button.filter.alttext.text">
				<bean:message key="main.general.button.filter.text" />
			</html:submit>
		</td>
	</tr>
</html:form>
	<bean:size id="customersSize" name="customers" />
	<c:if test="${customersSize>10}">
		<c:if test="${authorizedUser.manager}">
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
			title="Info"><b>Info</b></th>
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
		<c:if test="${authorizedUser.manager}">
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
			<!-- Info -->
			<td align="center">
			<div class="tooltip" id="info<c:out value='${customer.id}' />">
			<table>
				<tr>
					<td class="info">id:</td>
					<td class="info" colspan="3"><c:out
						value="${customer.id}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.created" />:</td>
					<td class="info"><c:out value="${customer.created}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${customer.createdby}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.edited" />:</td>
					<td class="info"><c:out value="${customer.lastupdate}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${customer.lastupdatedby}" /></td>
				</tr>
			</table>

			</div>
			<img
				onMouseOver="showWMTT(this,'info<c:out value="${customer.id}" />')"
				onMouseOut="hideWMTT()" width="12px" height="12px"
				src="/images/info_button.gif" />
			</td>
		<td><c:out value="${customer.shortname}" /></td>
		<td><c:out value="${customer.name}" /></td>
		<td><c:out value="${customer.address}" /></td>

		<c:if test="${authorizedUser.manager}">
			<td align="center"><html:link
				href="/do/EditCustomer?cuId=${customer.id}">
				<img src="/images/Edit.gif" alt="Edit Customer" title="<bean:message key="main.headlinedescription.customers.edit.text"/>"/>
			</html:link></td>
			<html:form action="/DeleteCustomer">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${customer.id})"
					src="/images/Delete.gif" alt="Delete Customer" titleKey="main.headlinedescription.customers.delete.text"/></td>
			</html:form>
		</c:if>
		</tr>
	</c:forEach>
	<c:if test="${authorizedUser.manager}">
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
<br><br>
</body>
</html:html>
