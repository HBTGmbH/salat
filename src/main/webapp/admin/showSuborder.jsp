<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="/WEB-INF/treeTag.tld" prefix="myjsp"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
	<bean:message key="main.general.mainmenu.suborders.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<link href="/tb/style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />
<script src="/tb/scripts/jquery-1.11.3.min.js" type="text/javascript"></script>
<script src="/tb/scripts/select2.full.min.js" type="text/javascript"></script>

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
	
	function setflag(form) {	
		var agree=confirm("<bean:message key="main.general.confirmsetflag.text" />");
		if (agree) {
			form.action = "/tb/do/ShowSuborder?task=setflag";
			form.submit();
		}
	}
	
	function multipleChange(form) {
	
		var checkedNoResetChoice = document.getElementById("noResetChoice").checked;
		var checkedSuborder = false;
		var checkedSuborderDefault = false;
		for (var i=0; i<form.length; i++) {
			if (form[i].id == "suborderIdArray") {
				if (form[i].checked == true) {
					checkedSuborder = true;
				}
				if (form[i].defaultChecked == true) {
					checkedSuborderDefault = true;
				}
			}
		}
		
		if (checkedSuborder == true || (checkedNoResetChoice == true && checkedSuborderDefault == true)) {
	  	if (document.getElementById("suborderOption").selectedIndex == 1) {
	    	var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
			if (agree) {
				form.action = "/tb/do/ShowSuborder?task=multiplechange";
				form.submit();
			} else {
				var element = document.getElementById("suborderOption");
				selectDropdownOption(element, 0);
				return false;
			}
	  	}
		if (document.getElementById("suborderOption").selectedIndex == 2) {
	    	var Eingabe = window.prompt("<bean:message key="main.general.confirmchangesubordercustomer.text" />", "");
	    	if (Eingabe){
		    	document.getElementById("suborderOptionValue").value = Eingabe;
		    	form.action = "/tb/do/ShowSuborder?task=multiplechange";
				form.submit();
			} else {
				var element = document.getElementById("suborderOption");
				selectDropdownOption(element, 0);
				return false;
			}
	  	}
	  	if (document.getElementById("suborderOption").selectedIndex == 3) {
	    	var Eingabe = window.prompt("<bean:message key="main.general.confirmchangehourlyrate.text" />", "");
	    	if (Eingabe) {
		    	document.getElementById("suborderOptionValue").value = Eingabe;
		    	form.action = "/tb/do/ShowSuborder?task=multiplechange";
				form.submit();
			} else {
				var element = document.getElementById("suborderOption");
				selectDropdownOption(element, 0);
				return false;
			}
	  	}
		}
	}
	
	function selectDropdownOption(element,wert)	{
		for (var i=0; i<element.options.length; i++) {
			if (element.options[i].value == wert) {
				element.options[i].selected = true;		
			}
			else {
				element.options[i].selected = false;	
			}
		}
	}
	
   	function showWMTT(Trigger,id) {
		wmtt = document.getElementById(id);
    	var hint;
		hint = Trigger.getAttribute("hint");
    	wmtt.style.display = "block";
	}

	function hideWMTT() {
		wmtt.style.display = "none";
	}
	
	function callEdit(form, soId) {
		form.action = "/tb/do/EditSuborder?soId=" + soId;
		form.submit();
	}

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'element'
		});	
	});		

</script>

</head>
<body>
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size: 14pt; font-weight: bold;"><br> <bean:message
			key="main.general.mainmenu.suborders.text" /><br> </span>
	<br>
	<span style="color: red"><html:errors footer="<br>" /> </span>

	<table class="center backgroundcolor">
		<html:form action="/ShowSuborder?task=refresh">
			<tr>
				<td class="noBborderStyle" colspan="2"><b><bean:message
							key="main.suborder.customerorder.text" /></b></td>
				<td class="noBborderStyle" colspan="9" align="left"><html:select
						property="customerOrderId" onchange="refresh(this.form)" styleClass="make-select2">
						<html:option value="-1">
							<bean:message key="main.general.allorders.text" />
						</html:option>
						<html:options collection="visibleCustomerOrders"
							labelProperty="signAndDescription" property="id" />
					</html:select></td>
			</tr>
			<tr>
				<td class="noBborderStyle" colspan="2"><b><bean:message
							key="main.general.filter.text" /></b></td>
				<td class="noBborderStyle" colspan="9" align="left"><html:text
						property="filter" size="40" /> <html:submit styleId="button"
						titleKey="main.general.button.filter.alttext.text">
						<bean:message key="main.general.button.filter.text" />
					</html:submit></td>
			</tr>
			<tr>
				<td class="noBborderStyle" colspan="2"><b><bean:message
							key="main.general.showexpired.text" /></b></td>
				<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
						property="show" onclick="refresh(this.form)" /></td>
			</tr>
			<tr>
				<td class="noBborderStyle" colspan="2"><b><bean:message
							key="main.general.showactualhoursflag.text" /></b></td>
				<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
						property="showActualHours" onclick="refresh(this.form)" /></td>
			</tr>
			<tr>
				<c:choose>
					<c:when test="${suborderCustomerOrderId == '-1'}">
						<td class="noBborderStyle" colspan="2"><b><bean:message
									key="main.general.showStructure.text" /></b></td>
						<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
								property="showstructure" onclick="refresh(this.form)"
								disabled="true" /></td>
					</c:when>
					<c:otherwise>
						<td class="noBborderStyle" colspan="2"><b><bean:message
									key="main.general.showStructure.text" /></b></td>
						<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
								property="showstructure" onclick="refresh(this.form)" /></td>

					</c:otherwise>
				</c:choose>
			</tr>
		</html:form>
		<bean:size id="subordersSize" name="suborders" />
		<c:if test="${subordersSize>10}">

			<c:if test="${(employeeAuthorized && visibleOrdersPresent) || employeeIsResponsible}">
				<tr>
					<td colspan="11" class="noBborderStyle">
						<table class="center backgroundcolor">
							<tr>
								<html:form action="/CreateSuborder">
									<td class="noBborderStyle"><html:submit styleId="button"
											titleKey="main.general.button.createsuborder.alttext.text">
											<bean:message key="main.general.button.createsuborder.text" />
										</html:submit></td>
								</html:form>
								<html:form action="/ShowSuborder">
									<td class="noBborderStyle"><c:if
											test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && (suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
											<html:submit styleId="button" onclick="setflag(this.form)"
												titleKey="main.general.button.setflag.alttext.text">
												<bean:message key="main.general.button.setflag.text" />
											</html:submit>
										</c:if></td>
								</html:form>
							</tr>
						</table>
					</td>

				</tr>
			</c:if>
		</c:if>

		<html:form action="/ShowSuborder">

			<c:choose>
				<c:when test="${showStructure}">
					<br>
					<%
						String browser = request.getHeader("User-Agent");
						org.apache.struts.util.PropertyMessageResources myMessages = (org.apache.struts.util.PropertyMessageResources) request
												.getAttribute("org.apache.struts.action.MESSAGE");
						String key = "main.employeeorder.openend.text";
						java.util.Locale myLocale = (java.util.Locale) session.getAttribute("org.apache.struts.action.LOCALE");
						String message = (String) myMessages.getMessage(myLocale, key);
					%>

					<tr>

						<c:choose>
							<c:when
								test="${(employeeAuthorized || currentOrder.responsible_hbt.id == loginEmployee.id) && (currentOrder.currentlyValid || !currentOrder.hide)}">
								<myjsp:tree mainProject="${currentOrder}"
									subProjects="${suborders}" browser="<%=browser%>"
									changeFunctionString="callEdit(this.form, 'default')"
									deleteFunctionString="confirmDelete(this.form, 'default')"
									onlySuborders="true" defaultString="default"
									currentSuborderID="0" endlessDate="<%=message%>" />
							</c:when>
							<c:otherwise>
								<myjsp:tree mainProject="${currentOrder}"
									subProjects="${suborders}" browser="<%=browser%>"
									onlySuborders="true" defaultString="default"
									currentSuborderID="0" endlessDate="<%=message%>" />
							</c:otherwise>
						</c:choose>
					</tr>
					<br>
					<td class="noBborderStyle" nowrap align="left"><img id="img1"
						src="/tb/images/Edit.gif" border="0"> <bean:message
							key="main.general.structureInstructionEdit.text" /></td>
					<br>
					<td class="noBborderStyle" nowrap align="left"><img id="img1"
						src="/tb/images/Delete.gif" border="0"> <bean:message
							key="main.general.structureInstructionDelete.text" /></td>
				</c:when>
				<c:otherwise>
					<tr>
						<c:if
							test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && (suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
							<th align="left" title="select">&nbsp;</th>
						</c:if>
						<th align="left" title="Info"><b>Info</b></th>
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.customerorder.text" />"><b><bean:message
									key="main.suborder.customerorder.text" /></b></th>
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.subordernumber.text" />"><b><bean:message
									key="main.suborder.sign.text" /></b></th>
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.suborder_customer.text" />"><b><bean:message
									key="main.suborder.suborder_customer.text" /></b></th>
						<th align="left"
							title="<bean:message
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
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.fixedprice.text" />"><b><bean:message
									key="main.suborder.fixedprice.text" /></b></th>
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.invoice.text" />"><b><bean:message
									key="main.suborder.invoice.text" /></b></th>
						<!--  
					<th align="left" title="<bean:message
						key="main.headlinedescription.suborders.currency.text" />"><b><bean:message
						key="main.suborder.currency.text" /></b></th>
					-->
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.hourlyrate.text" />"><b><bean:message
									key="main.suborder.hourlyrate.text" /></b></th>
						<th align="left"><b><bean:message
									key="main.general.debithours.text" /></b></th>
						<c:if test="${showActualHours}">
							<th align="left"><b><bean:message
										key="main.general.showactualhours.text" /></b></th>
							<th align="left"><b><bean:message
										key="main.general.difference.text" /></b></th>
							<th align="left"><b><bean:message
										key="main.general.showactualhours.not.invoiceable.text" /></b></th>
						</c:if>
						<th align="left"
							title="<bean:message
						key="main.headlinedescription.suborders.edit.text" />"><b><bean:message
									key="main.suborder.edit.text" /></b></th>
						<th align="left"
							title="<bean:message
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

						<!-- Checkbox -->
						<c:if
							test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && 
									(suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
							<td align="center"><html:multibox styleId="suborderIdArray"
									property="suborderIdArray" value="${suborder.id}" /></td>
						</c:if>

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
										<td class="info"><c:choose>
												<c:when test="${suborder.standard == true}">
													<bean:message key="main.general.yes" />
												</c:when>
												<c:otherwise>
													<bean:message key="main.general.no" />
												</c:otherwise>
											</c:choose></td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message
												key="main.general.commentnecessary" />:</td>
										<td class="info"><c:choose>
												<c:when test="${suborder.commentnecessary == true}">
													<bean:message key="main.general.yes" />
												</c:when>
												<c:otherwise>
													<bean:message key="main.general.no" />
												</c:otherwise>
											</c:choose></td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message
												key="main.suborder.trainingflag.text" />:</td>
										<td class="info"><c:choose>
												<c:when test="${suborder.trainingFlag == true}">
													<bean:message key="main.general.yes" />
												</c:when>
												<c:otherwise>
													<bean:message key="main.general.no" />
												</c:otherwise>
											</c:choose></td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message
												key="main.suborder.eocpossible.text" />:</td>
										<td class="info"><c:choose>
												<c:when test="${suborder.noEmployeeOrderContent == true}">
													<bean:message key="main.general.yes" />
												</c:when>
												<c:otherwise>
													<bean:message key="main.general.no" />
												</c:otherwise>
											</c:choose></td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message
												key="main.general.hide" />:</td>
										<td class="info"><c:choose>
												<c:when test="${suborder.hide == true}">
													<bean:message key="main.general.yes" />
												</c:when>
												<c:otherwise>
													<bean:message key="main.general.no" />
												</c:otherwise>
											</c:choose></td>
									</tr>
								</table>

							</div> <img
							onMouseOver="showWMTT(this,'info<c:out value="${suborder.id}" />')"
							onMouseOut="hideWMTT()" width="12px" height="12px"
							src="/tb/images/info_button.gif" />
						</td>

						<!-- invalid suborders should be gray -->
						<c:choose>
							<c:when test="${suborder.currentlyValid}">
								<td
									title="<c:out value="${suborder.customerorder.description}" />"><c:out
										value="${suborder.customerorder.sign}" /></td>
								<td><c:out value="${suborder.sign}" /></td>
								<td><c:if test="${suborder.suborder_customer == null}">&nbsp;</c:if>
									<c:out value="${suborder.suborder_customer}" /></td>
								<td><c:out value="${suborder.shortdescription}" /></td>
								<!-- <td><c:out value="${suborder.description}" /></td> -->

								<c:choose>
									<c:when test="${!suborder.timePeriodFitsToUpperElement}">
										<td style="color: red;"><c:out
												value="${suborder.fromDate}" /></td>
										<td style="color: red;"><c:choose>
												<c:when test="${suborder.untilDate == null}">
													<bean:message key="main.general.open.text" />
												</c:when>
												<c:otherwise>
													<c:out value="${suborder.untilDate}" />
												</c:otherwise>
											</c:choose></td>
									</c:when>
									<c:otherwise>
										<td><c:out value="${suborder.fromDate}" /></td>
										<td><c:choose>
												<c:when test="${suborder.untilDate == null}">
													<bean:message key="main.general.open.text" />
												</c:when>
												<c:otherwise>
													<c:out value="${suborder.untilDate}" />
												</c:otherwise>
											</c:choose></td>
									</c:otherwise>
								</c:choose>

								<!-- fixed price offer? -->
								<td align="center"><c:choose>
										<c:when test="${suborder.fixedPrice}">
											<bean:message key="main.general.yes" />
										</c:when>
										<c:otherwise>
											<bean:message key="main.general.no" />
										</c:otherwise>
									</c:choose></td>

								<!-- is hourly rate for billable suborders set? -->
								<c:choose>
									<c:when
										test="${suborder.invoiceString == 'Y' && suborder.hourly_rate == 0.0}">
										<td align="center" style="color: red"><c:choose>
												<c:when test="${suborder.invoiceString == 'Y'}">
													<bean:message key="main.suborder.invoice.yes.text" />
												</c:when>
												<c:when test="${suborder.invoiceString == 'N'}">
													<bean:message key="main.suborder.invoice.no.text" />
												</c:when>
												<c:when test="${suborder.invoiceString == 'U'}">
													<bean:message key="main.suborder.invoice.undefined.text" />
												</c:when>
											</c:choose></td>
										<td style="color: red"><c:out
												value="${suborder.hourly_rate}" /> <c:out
												value="${suborder.currency}" /></td>
									</c:when>
									<c:otherwise>
										<td align="center"><c:choose>
												<c:when test="${suborder.invoiceString == 'Y'}">
													<bean:message key="main.suborder.invoice.yes.text" />
												</c:when>
												<c:when test="${suborder.invoiceString == 'N'}">
													<bean:message key="main.suborder.invoice.no.text" />
												</c:when>
												<c:when test="${suborder.invoiceString == 'U'}">
													<bean:message key="main.suborder.invoice.undefined.text" />
												</c:when>
											</c:choose></td>
										<td><c:choose>
												<c:when test="${suborder.hourly_rate == 0.0}">
													&nbsp;
												</c:when>
												<c:otherwise>
													<fmt:formatNumber value="${suborder.hourly_rate}"
														minFractionDigits="2" />&nbsp;<c:out
														value="${suborder.currency}" />
												</c:otherwise>
											</c:choose></td>
									</c:otherwise>
								</c:choose>

								<td><c:choose>
										<c:when test="${suborder.debithours == null}">
											&nbsp;
										</c:when>
										<c:otherwise>
											<fmt:formatNumber value="${suborder.debithours}"
												minFractionDigits="2" />
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
									</c:choose></td>

								<c:if test="${showActualHours}">
									<td align="right"><fmt:formatNumber
											value="${suborder.duration}" minFractionDigits="2" /></td>
									<td align="right"><c:choose>
											<c:when
												test="${suborder.difference != null && (suborder.difference < 0.0 || suborder.difference >= 0.0)&&(suborder.debithoursunit != 0 && suborder.debithoursunit != 1 && suborder.debithoursunit != 12)}">
												<font color="#0000FF"><fmt:formatNumber
														value="${suborder.difference}" minFractionDigits="2" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && suborder.difference < 0.0}">
												<font color="#FF7777"><fmt:formatNumber
														value="${suborder.difference}" minFractionDigits="2" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && suborder.difference >= 0.0}">
												<fmt:formatNumber value="${suborder.difference}"
													minFractionDigits="2" />
											</c:when>
											<c:otherwise>
												&nbsp;
											</c:otherwise>
										</c:choose></td>
									<td align="right"><fmt:formatNumber
											value="${suborder.durationNotInvoiceable}" minFractionDigits="2" /></td>
								</c:if>
							</c:when>
							<c:otherwise>
								<!-- suborder is invalid -->
								<td style="color: gray"
									title="<c:out value="${suborder.customerorder.description}" />"><c:out
										value="${suborder.customerorder.sign}" /></td>
								<td style="color: gray"><c:out value="${suborder.sign}" /></td>
								<td style="color: gray"><c:out
										value="${suborder.suborder_customer}" /></td>
								<td style="color: gray"><c:out
										value="${suborder.shortdescription}" /></td>
								<!-- <td style="color:gray"><c:out value="${suborder.description}" /></td> -->

								<c:choose>
									<c:when test="${!suborder.timePeriodFitsToUpperElement}">
										<td style="color: red;"><c:out
												value="${suborder.fromDate}" /></td>
										<td style="color: red;"><c:choose>
												<c:when test="${suborder.untilDate == null}">
													<bean:message key="main.general.open.text" />
												</c:when>
												<c:otherwise>
													<c:out value="${suborder.untilDate}" />
												</c:otherwise>
											</c:choose></td>
									</c:when>
									<c:otherwise>
										<td style="color: gray"><c:out
												value="${suborder.fromDate}" /></td>
										<td style="color: gray"><c:choose>
												<c:when test="${suborder.untilDate == null}">
													<bean:message key="main.general.open.text" />
												</c:when>
												<c:otherwise>
													<c:out value="${suborder.untilDate}" />
												</c:otherwise>
											</c:choose></td>
									</c:otherwise>
								</c:choose>

								<!-- fixed price offer? -->
								<td style="color: gray" align="center"><c:choose>
										<c:when test="${suborder.fixedPrice}">
											<bean:message key="main.general.yes" />
										</c:when>
										<c:otherwise>
											<bean:message key="main.general.no" />
										</c:otherwise>
									</c:choose></td>

								<!-- is hourly rate for billable suborders set? -->
								<c:choose>
									<c:when	test="${suborder.invoiceString == 'Y' && suborder.hourly_rate == 0.0}">
										<td align="center" style="color: red">
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
										<td style="color: red"><fmt:formatNumber
												value="${suborder.hourly_rate}" minFractionDigits="2" /> <c:out
												value="${suborder.currency}" /></td>
									</c:when>
									<c:otherwise>
										<td align="center" style="color: gray">
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
										<td style="color: gray"><c:choose>
												<c:when test="${suborder.hourly_rate == 0.0}">
													&nbsp;
												</c:when>
												<c:otherwise>
													<fmt:formatNumber value="${suborder.hourly_rate}"
														minFractionDigits="2" />&nbsp;<c:out
														value="${suborder.currency}" />
												</c:otherwise>
											</c:choose></td>
									</c:otherwise>
								</c:choose>

								<td style="color: gray">
									<c:choose>
										<c:when test="${suborder.debithours == null}">
											&nbsp;
										</c:when>
										<c:otherwise>
											<fmt:formatNumber value="${suborder.debithours}"
												minFractionDigits="2" />
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

								<c:if test="${showActualHours}">
									<td align="right" style="color: gray"><fmt:formatNumber	value="${suborder.duration}" minFractionDigits="2" /></td>
									<td align="right" style="color: gray">
										<c:choose>
											<c:when
												test="${suborder.difference != null && (suborder.difference < 0.0 || suborder.difference >= 0.0)&&(suborder.debithoursunit != 0 && suborder.debithoursunit != 1 && suborder.debithoursunit != 12)}">
												<font color="#0000FF"><fmt:formatNumber
														value="${suborder.difference}" minFractionDigits="2" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && suborder.difference < 0.0}">
												<font color="#FF0000"><fmt:formatNumber
														value="${suborder.difference}" minFractionDigits="2" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && suborder.difference >= 0.0}">
												<fmt:formatNumber value="${suborder.difference}"
													minFractionDigits="2" />
											</c:when>
											<c:otherwise>
												&nbsp;
											</c:otherwise>
										</c:choose>
									</td>
									<td align="right" style="color: gray"><fmt:formatNumber value="${suborder.durationNotInvoiceable}" minFractionDigits="2" /></td>
								</c:if>
							</c:otherwise>
						</c:choose>

						<c:choose>
							<c:when
								test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && (suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
								<td align="center"><html:link
										href="/tb/do/EditSuborder?soId=${suborder.id}">
										<img src="/tb/images/Edit.gif" alt="Edit Suborder"
											title="<bean:message key="main.headlinedescription.suborders.edit.text"/>" />
									</html:link></td>
								<td align="center"><html:image
										onclick="confirmDelete(this.form, ${suborder.id})"
										src="/tb/images/Delete.gif" alt="Delete Suborder"
										titleKey="main.headlinedescription.suborders.delete.text" /></td>
							</c:when>
							<c:otherwise>
								<td align="center"><img height="12px" width="12px"
									src="/tb/images/verbot.gif" alt="Edit Suborder"
									title="<bean:message key="main.headlinedescription.suborders.accessdenied.text"/>" /></td>
								<td align="center"><img height="12px" width="12px"
									src="/tb/images/verbot.gif" alt="Delete Suborder"
									title="<bean:message key="main.headlinedescription.suborders.accessdenied.text"/>" /></td>
							</c:otherwise>
						</c:choose>
						</tr>
					</c:forEach>
				</c:otherwise>
			</c:choose>
			<c:if
				test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && (suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
				<tr>
					<html:hidden styleId="suborderOptionValue"
						property="suborderOptionValue" />
					<td class="noBborderStyle"><html:select
							styleId="suborderOption" property="suborderOption"
							onchange="multipleChange(this.form)">
							<html:option value="">
								<bean:message key="main.suborder.suborderoption.choose.text" />
							</html:option>
							<html:option value="delete">
								<bean:message key="main.suborder.suborderoption.delete.text" />
							</html:option>
							<html:option value="altersubordercustomer">
								<bean:message
									key="main.suborder.suborderoption.subordercustomer.text" />
							</html:option>
							<html:option value="alterhourlyrate">
								<bean:message key="main.suborder.suborderoption.hourlyrate.text" />
							</html:option>
						</html:select><span style="color: red"><html:errors
								property="suborderOption" />
							<html:errors property="bla" /></span></td>
					<td class="noBborderStyle"><b><bean:message
								key="main.general.button.resetChoice.text" />:</b></td>
					<td class="noBborderStyle" align="left"><html:checkbox
							styleId="noResetChoice" property="noResetChoice" /></td>
				</tr>
			</c:if>
		</html:form>
		<c:if
			test="${(employeeAuthorized && visibleOrdersPresent) || employeeIsResponsible}">
			<tr>
				<td colspan="11" class="noBborderStyle">
					<table class="center backgroundcolor">
						<tr>
							<html:form action="/CreateSuborder">
								<td class="noBborderStyle"><html:submit styleId="button"
										titleKey="main.general.button.createsuborder.alttext.text">
										<bean:message key="main.general.button.createsuborder.text" />
									</html:submit></td>
							</html:form>
							<html:form action="/ShowSuborder">
								<td class="noBborderStyle"><c:if
										test="${(employeeAuthorized || suborder.customerorder.responsible_hbt.id == loginEmployee.id) && (suborder.customerorder.currentlyValid || !suborder.customerorder.hide)}">
										<html:submit styleId="button" onclick="setflag(this.form)"
											titleKey="main.general.button.setflag.alttext.text">
											<bean:message key="main.general.button.setflag.text" />
										</html:submit>
									</c:if></td>
							</html:form>
						</tr>
					</table>
				</td>
			</tr>
		</c:if>
	</table>
	<br>
	<br>
	<br>
	<br>
	<br>
	<br>
	<br>
	<br>
	<br>
	<br>
</body>
</html:html>
