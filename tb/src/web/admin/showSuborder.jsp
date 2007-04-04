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
	
	function refresh(form) {	
		form.action = "/tb/do/ShowSuborder?task=refresh";
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
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.suborders.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>"/>
</span>

<table class="center backgroundcolor">
<html:form action="/ShowSuborder?task=refresh">
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message
			key="main.suborder.customerorder.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left">
			<html:select property="customerOrderId" onchange="refresh(this.form)">
				<html:option value="-1">
					<bean:message key="main.general.allorders.text" />
				</html:option>
				<html:options collection="visibleCustomerOrders" labelProperty="signAndDescription"
					property="id" />
		</html:select>
		</td>
	</tr>
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.filter.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left">
			<html:text property="filter" size="40" />
			<html:submit styleId="button" titleKey="main.general.button.filter.alttext.text">
				<bean:message key="main.general.button.filter.text" />
			</html:submit>
		</td>
	</tr>
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.showinvalid.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
				property="show" onclick="refresh(this.form)" /> </td>
	</tr>
</html:form>
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
		<th align="left"
			title="Info"><b>Info</b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.customerorder.text" />"><b><bean:message
			key="main.suborder.customerorder.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.subordernumber.text" />"><b><bean:message
			key="main.suborder.sign.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.description.text" />"><b><bean:message
			key="main.suborder.shortdescription.short.text" /></b></th>
		<!--<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.description.text" />"><b><bean:message
			key="main.suborder.description.text" /></b></th>-->
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.validfrom.text" />"><b><bean:message
			key="main.customerorder.validfrom.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.validuntil.text" />"><b><bean:message
			key="main.customerorder.validuntil.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.invoice.text" />"><b><bean:message
			key="main.suborder.invoice.text" /></b></th>
		<!--  
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.currency.text" />"><b><bean:message
			key="main.suborder.currency.text" /></b></th>
		-->
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.hourlyrate.text" />"><b><bean:message
			key="main.suborder.hourlyrate.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.general.debithours.text" /></b></th>
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

			<!-- Info -->
			<td align="center">
			<div class="tooltip" id="info<c:out value='${suborder.id}' />">
			<table>
				<tr>
					<td class="info">id:</td>
					<td class="info" colspan="3"><c:out
						value="${suborder.id}" /></td>
				</tr>
				<tr>
					<td class="info"><bean:message
						key="main.timereport.tooltip.order" />:</td>
					<td class="info" colspan="3"><c:out
						value="${suborder.customerorder.sign}" /></td>
				</tr>
				<tr>
					<td class="info">&nbsp;</td>
					<td class="info" colspan="3"><c:out
						value="${suborder.customerorder.description}" /></td>
				</tr>
				<tr>
					<td class="info"><bean:message
						key="main.timereport.tooltip.suborder" />:</td>
					<td class="info" colspan="3"><c:out
						value="${suborder.sign}" /></td>
				</tr>
				<tr>
					<td class="info">&nbsp;</td>
					<td class="info" colspan="3"><c:out
						value="${suborder.description}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.created" />:</td>
					<td class="info"><c:out value="${suborder.created}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${suborder.createdby}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.edited" />:</td>
					<td class="info"><c:out value="${suborder.lastupdate}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${suborder.lastupdatedby}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.general.standard" />:</td>
					<td class="info"><c:choose><c:when test="${suborder.standard == true}"><bean:message
						key="main.general.yes" /></c:when><c:otherwise><bean:message
						key="main.general.no" /></c:otherwise></c:choose></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.general.commentnecessary" />:</td>
					<td class="info"><c:choose><c:when test="${suborder.commentnecessary == true}"><bean:message
						key="main.general.yes" /></c:when><c:otherwise><bean:message
						key="main.general.no" /></c:otherwise></c:choose></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.general.hide" />:</td>
					<td class="info"><c:choose><c:when test="${suborder.hide == true}"><bean:message
						key="main.general.yes" /></c:when><c:otherwise><bean:message
						key="main.general.no" /></c:otherwise></c:choose></td>
				</tr>
			</table>

			</div>
			<img
				onMouseOver="showWMTT(this,'info<c:out value="${suborder.id}" />')"
				onMouseOut="hideWMTT()" width="12px" height="12px"
				src="/tb/images/info_button.gif" />
			</td>

		<!-- invalid suborders should be gray -->
		<c:choose>
			<c:when test="${suborder.currentlyValid}">
				<td title="<c:out value="${suborder.customerorder.description}" />"><c:out value="${suborder.customerorder.sign}" /></td>
				<td><c:out value="${suborder.sign}" /></td>
				<td><c:out value="${suborder.shortdescription}" /></td>
				<!-- <td><c:out value="${suborder.description}" /></td> -->
				<td><c:out value="${suborder.fromDate}" /></td>
				<td>
					<c:choose>
						<c:when test="${suborder.untilDate == null}">
							<bean:message key="main.general.open.text" />
						</c:when>
						<c:otherwise>
							<c:out value="${suborder.untilDate}" />
						</c:otherwise>
					</c:choose>
				</td>
				
				<!-- is hourly rate for billable suborders set? -->
				<c:choose>
					<c:when test="${suborder.invoiceString == 'Y' && suborder.hourly_rate == 0.0}">
						<td align="center" style="color:red">
							<c:choose>
								<c:when test="${suborder.invoiceString == 'Y'}">
									<bean:message key="main.suborder.invoice.yes.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'N'}">
									<bean:message key="main.suborder.invoice.no.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'U'}">
									<bean:message key="main.suborder.invoice.undefined.text" />
								</c:when>
							</c:choose>
						</td>
						<td style="color:red"><c:out value="${suborder.hourly_rate}" /> <c:out value="${suborder.currency}" /></td>
					</c:when>
					<c:otherwise>
						<td align="center">
							<c:choose>
								<c:when test="${suborder.invoiceString == 'Y'}">
									<bean:message key="main.suborder.invoice.yes.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'N'}">
									<bean:message key="main.suborder.invoice.no.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'U'}">
									<bean:message key="main.suborder.invoice.undefined.text" />
								</c:when>
							</c:choose>
						</td>
						<td><c:out value="${suborder.hourly_rate}" /> <c:out value="${suborder.currency}" /></td>
					</c:otherwise>
				</c:choose>
				
				<td>
					<c:choose>
						<c:when test="${suborder.debithours == null}">
							&nbsp;
						</c:when>
						<c:otherwise>
							<c:out value="${suborder.debithours}" />
							<c:choose>
								<c:when test="${suborder.debithoursunit == 0}">
									/ <bean:message key="main.general.totaltime.text" />
								</c:when>
								<c:when test="${suborder.debithoursunit == 1}">
									/ <bean:message key="main.general.year.text" />
								</c:when>
								<c:when test="${suborder.debithoursunit == 12}">
									/ <bean:message key="main.general.month.text" />
								</c:when>
								<c:otherwise>
									?
								</c:otherwise>
							</c:choose>
						</c:otherwise>
					</c:choose>
				</td>
			</c:when>
			<c:otherwise>
			<!-- suborder is invalid -->
				<td style="color:gray" title="<c:out value="${suborder.customerorder.description}" />"><c:out value="${suborder.customerorder.sign}" /></td>
				<td style="color:gray"><c:out value="${suborder.sign}" /></td>
				<td style="color:gray"><c:out value="${suborder.shortdescription}" /></td>
				<!-- <td style="color:gray"><c:out value="${suborder.description}" /></td> -->
				<td style="color:gray"><c:out value="${suborder.fromDate}" /></td>
				<td style="color:gray">
					<c:choose>
						<c:when test="${suborder.untilDate == null}">
							<bean:message key="main.general.open.text" />
						</c:when>
						<c:otherwise>
							<c:out value="${suborder.untilDate}" />
						</c:otherwise>
					</c:choose>
				</td>
				
				<!-- is hourly rate for billable suborders set? -->
				<c:choose>
					<c:when test="${suborder.invoiceString == 'Y' && suborder.hourly_rate == 0.0}">
						<td align="center" style="color:red">
							<c:choose>
								<c:when test="${suborder.invoiceString == 'Y'}">
									<bean:message key="main.suborder.invoice.yes.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'N'}">
									<bean:message key="main.suborder.invoice.no.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'U'}">
									<bean:message key="main.suborder.invoice.undefined.text" />
								</c:when>
							</c:choose>
						</td>
						<td style="color:red"><c:out value="${suborder.hourly_rate}" /> <c:out value="${suborder.currency}" /></td>
					</c:when>
					<c:otherwise>
						<td align="center" style="color:gray">
							<c:choose>
								<c:when test="${suborder.invoiceString == 'Y'}">
									<bean:message key="main.suborder.invoice.yes.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'N'}">
									<bean:message key="main.suborder.invoice.no.text" />
								</c:when>
								<c:when test="${suborder.invoiceString == 'U'}">
									<bean:message key="main.suborder.invoice.undefined.text" />
								</c:when>
							</c:choose>
						</td>
						<td style="color:gray"><c:out value="${suborder.hourly_rate}" /> <c:out value="${suborder.currency}" /></td>
					</c:otherwise>
				</c:choose>
				
				<td style="color:gray">
					<c:choose>
						<c:when test="${suborder.debithours == null}">
							&nbsp;
						</c:when>
						<c:otherwise>
							<c:out value="${suborder.debithours}" />
							<c:choose>
								<c:when test="${suborder.debithoursunit == 0}">
									/ <bean:message key="main.general.totaltime.text" />
								</c:when>
								<c:when test="${suborder.debithoursunit == 1}">
									/ <bean:message key="main.general.year.text" />
								</c:when>
								<c:when test="${suborder.debithoursunit == 12}">
									/ <bean:message key="main.general.month.text" />
								</c:when>
								<c:otherwise>
									?
								</c:otherwise>
							</c:choose>
						</c:otherwise>
					</c:choose>
				</td>
			</c:otherwise>
		</c:choose>

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
<br><br><br><br><br><br><br><br><br><br>
</body>
</html:html>
