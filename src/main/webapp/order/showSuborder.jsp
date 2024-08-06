<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/tree" prefix="tree"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
<head>

<title><bean:message key="main.general.application.title" /> -
	<bean:message key="main.general.mainmenu.suborders.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />

<script type="text/javascript" language="JavaScript">

	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/do/DeleteSuborder?soId=" + id;
			form.submit();
		}
	}	
	
	function refresh(form) {	
		form.action = "/do/ShowSuborder?task=refresh";
		form.submit();
	}
	
	function setflag(form) {	
		var agree=confirm("<bean:message key="main.general.confirmsetflag.text" />");
		if (agree) {
			form.action = "/do/ShowSuborder?task=setflag";
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
					form.action = "/do/ShowSuborder?task=multiplechange";
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
					form.action = "/do/ShowSuborder?task=multiplechange";
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
		form.action = "/do/EditSuborder?soId=" + soId;
		form.submit();
	}

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'auto'
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

			<c:if test="${(authorizedUser.manager && visibleOrdersPresent) || employeeIsResponsible}">
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
											test="${(authorizedUser.manager || suborder.customerorder.responsible_hbt.id == loginEmployee.id)}">
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
								test="${(authorizedUser.manager || currentOrder.responsible_hbt.id == loginEmployee.id)}">
								<tree:tree mainProject="${currentOrder}"
									subProjects="${suborders}" browser="<%=browser%>"
									changeFunctionString="callEdit(this.form, 'default')"
									deleteFunctionString="confirmDelete(this.form, 'default')"
									onlySuborders="true" defaultString="default"
									currentSuborderID="0" endlessDate="<%=message%>" />
							</c:when>
							<c:otherwise>
								<tree:tree mainProject="${currentOrder}"
									subProjects="${suborders}" browser="<%=browser%>"
									onlySuborders="true" defaultString="default"
									currentSuborderID="0" endlessDate="<%=message%>" />
							</c:otherwise>
						</c:choose>
					</tr>
					<br>
					<td class="noBborderStyle" nowrap align="left"><img id="img1"
																		src="<c:url value="/images/Edit.gif"/>" border="0"> <bean:message
							key="main.general.structureInstructionEdit.text" /></td>
					<br>
					<td class="noBborderStyle" nowrap align="left"><img id="img1"
																		src="<c:url value="/images/Delete.gif"/>" border="0"> <bean:message
							key="main.general.structureInstructionDelete.text" /></td>
				</c:when>
				<c:otherwise>
					<tr>
						<c:if test="${(authorizedUser.manager || suborder.customerorder.responsible_hbt.id == loginEmployee.id)}">
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
						<c:choose>
							<c:when test="${(authorizedUser.manager || suborder.customerorder.responsible_hbt.id == loginEmployee.id)}">
								<td align="center"><html:multibox styleId="suborderIdArray"
																  property="suborderIdArray" value="${suborder.id}" /></td>
							</c:when>
							<c:otherwise>
								<td align="center">&nbsp;</td>
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
							src="<c:url value="/images/info_button.gif"/>" />
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
								<td align="center">
									<c:choose>
										<c:when test="${suborder.fixedPrice}">
											<bean:message key="main.general.yes" />
										</c:when>
										<c:otherwise>
											<bean:message key="main.general.no" />
										</c:otherwise>
									</c:choose>
								</td>

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

								<td><c:choose>
										<c:when test="${suborder.debithours == null || suborder.debithours.zero}">
											&nbsp;
										</c:when>
										<c:otherwise>
											<java8:formatDuration value="${suborder.debithours}" />
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
									<td align="right">
										<java8:formatDuration value="${suborder.duration}" />
									</td>
									<td align="right"><c:choose>
											<c:when test="${suborder.difference != null && suborder.debithoursunit != 0 && suborder.debithoursunit != 1 && suborder.debithoursunit != 12}">
												<font color="#0000FF"><java8:formatDuration
														value="${suborder.difference}" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && suborder.difference.negative}">
												<font color="#FF7777"><java8:formatDuration
														value="${suborder.difference}" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && !suborder.difference.negative}">
												<java8:formatDuration
														value="${suborder.difference}" />
											</c:when>
										</c:choose></td>
									<td align="right">
										<java8:formatDuration value="${suborder.durationNotInvoiceable}" />
									</td>
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
									</c:choose>
								</td>

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

								<td style="color: gray">
									<c:choose>
										<c:when test="${suborder.debithours == null || suborder.debithours.zero}">
											&nbsp;
										</c:when>
										<c:otherwise>
											<java8:formatDuration value="${suborder.debithours}" />
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
									<td align="right" style="color: gray"><java8:formatDuration	value="${suborder.duration}" /></td>
									<td align="right" style="color: gray">
										<c:choose>
											<c:when
												test="${suborder.difference != null && suborder.debithoursunit != 0 && suborder.debithoursunit != 1 && suborder.debithoursunit != 12}">
												<font color="#0000FF"><java8:formatDuration
														value="${suborder.difference}"  /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && suborder.difference.negative}">
												<font color="#FF0000"><java8:formatDuration
														value="${suborder.difference}" /></font>
											</c:when>
											<c:when
												test="${suborder.difference != null && !suborder.difference.negative}">
												<java8:formatDuration value="${suborder.difference}" />
											</c:when>
											<c:otherwise>
												&nbsp;
											</c:otherwise>
										</c:choose>
									</td>
									<td align="right" style="color: gray"><java8:formatDuration value="${suborder.durationNotInvoiceable}" /></td>
								</c:if>
							</c:otherwise>
						</c:choose>

						<c:choose>
							<c:when	test="${(authorizedUser.manager || suborder.customerorder.responsible_hbt.id == loginEmployee.id)}">
								<td align="center"><html:link
										href="/do/EditSuborder?soId=${suborder.id}">
										<img src="<c:url value="/images/Edit.gif"/>" alt="Edit Suborder"
											 title="<bean:message key="main.headlinedescription.suborders.edit.text"/>" />
									</html:link></td>
								<td align="center"><html:image
										onclick="confirmDelete(this.form, ${suborder.id})"
										src="/images/Delete.gif" alt="Delete Suborder"
										titleKey="main.headlinedescription.suborders.delete.text" /></td>
							</c:when>
							<c:otherwise>
								<td align="center"><img height="12px" width="12px"
														src="<c:url value="/images/verbot.gif"/>" alt="Edit Suborder"
														title="<bean:message key="main.headlinedescription.suborders.accessdenied.text"/>" /></td>
								<td align="center"><img height="12px" width="12px"
														src="<c:url value="/images/verbot.gif"/>" alt="Delete Suborder"
														title="<bean:message key="main.headlinedescription.suborders.accessdenied.text"/>" /></td>
							</c:otherwise>
						</c:choose>
						</tr>
					</c:forEach>
				</c:otherwise>
			</c:choose>
			<c:if test="${(authorizedUser.manager || suborder.customerorder.responsible_hbt.id == loginEmployee.id)}">
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
						</html:select><span style="color: red"><html:errors
								property="suborderOption" /></span></td>
					<td class="noBborderStyle"><b><bean:message
								key="main.general.button.resetChoice.text" />:</b></td>
					<td class="noBborderStyle" align="left"><html:checkbox
							styleId="noResetChoice" property="noResetChoice" /></td>
				</tr>
			</c:if>
		</html:form>
		<c:if
			test="${(authorizedUser.manager && visibleOrdersPresent) || employeeIsResponsible}">
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
										test="${(authorizedUser.manager || suborder.customerorder.responsible_hbt.id == loginEmployee.id)}">
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
