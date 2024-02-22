<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@ taglib prefix="java8" uri="http://hbt.de/jsp/taglib/java8-date-formatting" %>
<html:html>
<head>

<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addcustomerorder.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />

<script type="text/javascript" language="JavaScript">

	function setDate(which, howMuch) {
		document.forms[0].action = "/do/StoreCustomerorder?task=setDate&which=" + which + "&howMuch=" + howMuch;
		document.forms[0].submit();
	}
	
	function setStoreAction(form, actionVal, addMore) {	
		form.action = "/do/StoreCustomerorder?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}
		
	function afterCalenderClick() {
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

<html:form action="/StoreCustomerorder">

	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message
		key="main.general.entercustomerorderproperties.text" />:<br></span>
	<br>

	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.customer.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="customerId">
				<html:options collection="customers" labelProperty="shortname"
					property="id" />
			</html:select></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.sign.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="sign" size="40"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="sign" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.validfrom.text" /></b></td>
			<td align="left" class="noBborderStyle"><!-- JavaScript Stuff for popup calender -->
			<script type="text/javascript" language="JavaScript"
				src="/scripts/CalendarPopup.js"></script> <script type="text/javascript"
				language="JavaScript">
                    document.write(getCalendarStyles());
                </script> <script type="text/javascript" language="JavaScript">
                    function calenderPopup(cal) {
                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
                    }
                    function calenderPopupFrom() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        //cal.select(document.forms[0].validFrom,'from','E yyyy-MM-dd');
                        cal.select(document.forms[0].validFrom,'from','yyyy-MM-dd');
                    }
                    function calenderPopupUntil() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        //cal.select(document.forms[0].validUntil,'until','E yyyy-MM-dd');
                        cal.select(document.forms[0].validUntil,'until','yyyy-MM-dd');
                    }
                </script> <html:text property="validFrom" readonly="false"
				size="12" maxlength="10" /> <a
				href="javascript:calenderPopupFrom()" name="from" ID="from"
				style="text-decoration:none;"> <img
						src="/images/popupcalendar.gif" width="22" height="22"
						alt="<bean:message key="main.date.popup.alt.text" />"
						style="border:0;vertical-align:top"> </a>
				
				<%-- Arrows for navigating the from-Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','-1')" title="<bean:message key="main.date.popup.prevday" />">
				<img src="/images/pfeil_links.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','0')" title="<bean:message key="main.date.popup.today" />">
				<img src="/images/pfeil_unten.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','1')" title="<bean:message key="main.date.popup.nextday" />">
				<img src="/images/pfeil_rechts.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				<span style="color:red"><html:errors
				property="validFrom" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.validuntil.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="validUntil" readonly="false" size="12" maxlength="10" />
			<a href="javascript:calenderPopupUntil()" name="until" ID="until"
				style="text-decoration:none;"> <img
					src="/images/popupcalendar.gif" width="22" height="22"
					alt="<bean:message key="main.date.popup.alt.text" />"
					style="border:0;vertical-align:top"> </a>
				
				<%-- Arrows for navigating the until-Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','-1')" title="<bean:message key="main.date.popup.prevday" />">
				<img src="/images/pfeil_links.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','0')" title="<bean:message key="main.date.popup.today" />">
				<img src="/images/pfeil_unten.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','1')" title="<bean:message key="main.date.popup.nextday" />">
				<img src="/images/pfeil_rechts.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				<span style="color:red"><html:errors
				property="validUntil" /></span></td>
		</tr>
		
		<!-- Bezeichnung -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.description.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:textarea
				property="description" cols="30" rows="4" /> <span style="color:red"><html:errors
				property="description" /></span></td>
		</tr>
		
		<!-- Kurzbezeichnung -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.shortdescription.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="shortdescription" size="20"
				maxlength="20" /> <span style="color:red"><html:errors
				property="shortdescription" /></span></td>
		</tr>
		
		<!-- Durchführungsverantwortlicher bei HBT -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblehbt.execution.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="employeeId">
					<c:forEach var="employee" items="${employeeswithcontract}">
						<html:option value="${employee.id}">
							<c:out value="${employee.name}" /> |
							<c:out value="${employee.sign}" />
						</html:option>
					</c:forEach>
				</html:select>
			</td>
		</tr>
		
		<!-- Vertragsverantwortlicher bei HBT -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblehbt.contract.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="respContrEmployeeId">
					<c:forEach var="employee" items="${employeeswithcontract}">
						<html:option value="${employee.id}">
							<c:out value="${employee.name}" /> |
							<c:out value="${employee.sign}" />
						</html:option>
					</c:forEach>
				</html:select>
			</td>
		</tr>
		
		<!-- Verantwortlicher beim Kunden (fachlich) -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblecustomer.tech.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="responsibleCustomerTechnical" size="40"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors
				property="responsibleCustomerTechnical" /></span></td>
		</tr>

		<!-- Verantwortlicher beim Kunden (vertraglich) -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblecustomer.contract.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="responsibleCustomerContractually" size="40"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="responsibleCustomerContractually" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.ordercustomer.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="orderCustomer" size="40"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="orderCustomer" /></span>
			</td>
		</tr>
		
		<!-- debithours -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.general.debithours.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="debithours" size="20" />&nbsp;&nbsp;<bean:message
				key="main.general.per.text" />&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio property="debithoursunit" value="12" disabled="false" /><bean:message
				key="main.general.month.text" /> <html:radio 
				property="debithoursunit" value="1" disabled="false" /><bean:message
				key="main.general.year.text" /> <html:radio 
				property="debithoursunit" value="0" disabled="false" /><bean:message
				key="main.general.totaltime.text" /> <span 
				style="color:red"><html:errors property="debithours" /></span>
			</td>
		</tr>
		
		<!-- Statusbericht -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.statusreport.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="statusreport">
					<html:option value="0"><bean:message
				key="main.customerorder.statusreport.option.0.text" /></html:option>
					<html:option value="12"><bean:message
				key="main.customerorder.statusreport.option.12.text" /></html:option>
					<html:option value="6"><bean:message
				key="main.customerorder.statusreport.option.6.text" /></html:option>
					<html:option value="4"><bean:message
				key="main.customerorder.statusreport.option.4.text" /></html:option>
				</html:select>
			</td>
		</tr>		
		
		<!-- hide -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.general.hideinselectboxes.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="hide" /> </td>
		</tr>

	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button"  titleKey="main.general.button.save.alttext.text">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button" titleKey="main.general.button.saveandcontinue.alttext.text">
				<bean:message key="main.general.button.saveandcontinue.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button" titleKey="main.general.button.reset.alttext.text">
				<bean:message key="main.general.button.reset.text" />
			</html:submit></td>
		</tr>
	</table>
	<html:hidden property="id" />

	<c:if test="${timereportsOutOfRange != null}">
		<table class="center backgroundcolor" width="100%">

			<tr>
				<th align="left"><b>Info</b></th>
				<th align="left"
					title="<bean:message
				key="main.headlinedescription.dailyoverview.employee.text" />"><b><bean:message
						key="main.timereport.monthly.employee.sign.text" /></b></th>
				<th align="left"
					title="<bean:message
				key="main.headlinedescription.dailyoverview.refday.text" />"><b><bean:message
						key="main.timereport.monthly.refday.text" /></b></th>
				<th align="left"
					title="<bean:message
				key="main.headlinedescription.dailyoverview.customerorder.text" />"><b><bean:message
						key="main.timereport.monthly.customerorder.text" /></b></th>
				<th align="left"
					title="<bean:message
				key="main.headlinedescription.dailyoverview.description.text" />"><b><bean:message
						key="main.customerorder.shortdescription.text" /></b></th>
				<th align="left"
					title="<bean:message
				key="main.headlinedescription.dailyoverview.taskdescription.text" />"
					width="25%"><b><bean:message
						key="main.timereport.monthly.taskdescription.text" /></b></th>
				<th align="center"
					title="<bean:message
				key="main.headlinedescription.dailyoverview.hours.text" />"><b><bean:message
						key="main.timereport.monthly.hours.text" /></b></th>
			</tr>

			<c:forEach var="timereport" items="${timereportsOutOfRange}"
					   varStatus="rowID">
				<c:choose>
					<c:when test="${rowID.count%2==0}">
						<tr class="primarycolor">
					</c:when>
					<c:otherwise>
						<tr class="secondarycolor">
					</c:otherwise>
				</c:choose>

				<!-- Info -->
				<td align="center">
					<div class="tooltip" id="info<c:out value='${timereport.id}' />">
						<table>
							<tr>
								<td class="info">id:</td>
								<td class="info" colspan="3"><c:out
										value="${timereport.id}" /></td>
							</tr>
							<tr>
								<td class="info"><bean:message
										key="main.timereport.tooltip.employee" />:</td>
								<td class="info" colspan="3">
									<c:out value="${timereport.employeeName}" />
								</td>
							</tr>
							<tr>
								<td class="info"><bean:message
										key="main.timereport.tooltip.order" />:</td>
								<td class="info" colspan="3"><c:out
										value="${timereport.customerorderSign}" /></td>
							</tr>
							<tr>
								<td class="info">&nbsp;</td>
								<td class="info" colspan="3"><c:out
										value="${timereport.customerorderDescription}" /></td>
							</tr>
							<tr>
								<td class="info"><bean:message
										key="main.timereport.tooltip.suborder" />:</td>
								<td class="info" colspan="3"><c:out
										value="${timereport.suborderSign}" /></td>
							</tr>
							<tr>
								<td class="info">&nbsp;</td>
								<td class="info" colspan="3"><c:out
										value="${timereport.suborderDescription}" /></td>
							</tr>
							<tr>
								<td class="info"><bean:message
										key="main.timereport.tooltip.status" />:</td>
								<td class="info"><c:out value="${timereport.status}" /></td>
							</tr>
							<tr>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.created" />:</td>
								<td class="info"><c:out value="${timereport.created}" /></td>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.by" /></td>
								<td class="info" valign="top"><c:out
										value="${timereport.createdby}" /></td>
							</tr>
							<tr>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.edited" />:</td>
								<td class="info"><c:out value="${timereport.lastupdate}" /></td>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.by" /></td>
								<td class="info" valign="top"><c:out
										value="${timereport.lastupdatedby}" /></td>
							</tr>
							<tr>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.released" />:</td>
								<td class="info"><c:out value="${timereport.released}" /></td>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.by" /></td>
								<td class="info" valign="top"><c:out
										value="${timereport.releasedby}" /></td>
							</tr>
							<tr>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.accepted" />:</td>
								<td class="info"><c:out value="${timereport.accepted}" /></td>
								<td class="info" valign="top"><bean:message
										key="main.timereport.tooltip.by" /></td>
								<td class="info" valign="top"><c:out
										value="${timereport.acceptedby}" /></td>
							</tr>
						</table>

					</div>
					<img
							onMouseOver="showWMTT(this,'info<c:out value="${timereport.id}" />')"
							onMouseOut="hideWMTT()" width="12px" height="12px"
							src="/images/info_button.gif" />
				</td>

				<!-- Mitarbeiter -->
				<td>
					<c:out value="${timereport.employeeSign}" />
				</td>

				<!-- Datum -->
				<td>
					<logic:equal name="timereport" property="holiday" value="true">
						<span style="color:red"><java8:formatLocalDate value="${timereport.referenceday}" /></span>
					</logic:equal>
					<logic:equal name="timereport" property="holiday" value="false">
						<java8:formatLocalDate value="${timereport.referenceday}" />
					</logic:equal>
				</td>

				<!-- Auftrag -->
				<td>
					<c:out value="${timereport.customerorderSign}" /><br>
					<c:out value="${timereport.suborderSign}" />
				</td>

				<!-- Bezeichnung -->
				<td>
					<c:out value="${timereport.customerorderDescription}" /><br>
					<c:out value="${timereport.suborderDescription}" />
				</td>

				<!-- Kommentar -->
				<td>
					<c:choose>
						<c:when test="${timereport.taskdescription eq ''}">
							&nbsp;
						</c:when>
						<c:otherwise>
							<c:out value="${timereport.taskdescription}" />
						</c:otherwise>
					</c:choose>
				</td>

				<!-- Dauer -->
				<td align="center" nowrap>
					<java8:formatDuration value="${timereport.duration}" />
				</td>

				</tr>
			</c:forEach>
		</table>
		<br><br>
	</c:if>
	
</html:form>
</body>
</html:html>
