<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
<head>

<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addemployeecontract.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
<script type="text/javascript" language="JavaScript">
	
	function setDate(which, howMuch) {
		document.forms[0].action = "/do/StoreEmployeecontract?task=setDate&which=" + which + "&howMuch=" + howMuch;
		document.forms[0].submit();
	}
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/do/StoreEmployeecontract?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}
		
	function afterCalenderClick() {
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
<span style="font-size:14pt;font-weight:bold;"><br><bean:message
	key="main.general.enteremployeecontractproperties.text" />:<br></span>
<br>
<html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />
<html:form action="/StoreEmployeecontract">
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.employee.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<c:choose>
					<c:when test="${employeeContractContext eq 'create'}">
						<html:select property="employee" styleClass="make-select2">
							<c:forEach var="employee" items="${employees}">
								<html:option value="${employee.id}">
									<c:out value="${employee.name}" /> |
									<c:out value="${employee.sign}" />
								</html:option>
							</c:forEach>
						</html:select>
						<span style="color:red"><html:errors property="employee" /></span>
					</c:when>
					<c:otherwise>
						<b><c:out value="${currentEmployee}" /></b>
					</c:otherwise>
				</c:choose>
			</td>
		</tr>
		
		
	<tr>
	  <td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.supervisor.text" /></b></td>
	  <td align="left" class="noBborderStyle">
		<html:select property="supervisorid" onchange="refresh(this.form)" styleClass="make-select2">
		  <html:options collection="empWithCont" labelProperty="name" property="id" />
		</html:select>
		<span style="color:red"><html:errors property="supervisorid" /></span>
	  </td>
	</tr>
		
		
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.taskdescription.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:textarea
				property="taskdescription" cols="40" rows="6" /> <span
				style="color:red"><html:errors property="taskdescription" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.validfrom.text" /></b></td>
			<td align="left" class="noBborderStyle">
		
			<!-- JavaScript Stuff for popup calender -->
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
				size="10" maxlength="10" /> <a
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
				key="main.employeecontract.validuntil.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="validUntil" readonly="false" size="10" maxlength="10" />
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

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.freelancer.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="freelancer" /> <span style="color:red"><html:errors
				property="freelancer" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.dailyworkingtime.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="dailyworkingtime" size="10" /> <span style="color:red"><html:errors
				property="dailyworkingtime" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.yearlyvacation.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="yearlyvacation" size="10" /> <span style="color:red"><html:errors
				property="yearlyvacation" /></span></td>
		</tr>
		
		<tr>
			<c:choose>
				<c:when test="${employeeContractContext eq 'create'}">
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.employeecontract.initialovertime.text" /></b></td>
					<td align="left" class="noBborderStyle"><html:text
						property="initialOvertime" size="10" /> <span style="color:red"><html:errors
						property="initialOvertime" /></span></td>
				</c:when>
				<c:otherwise>
					<td align="left" class="noBborderStyle"></td>	
				</c:otherwise>
			</c:choose>
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
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button" titleKey="main.general.button.save.alttext.text">
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

	<!-- overtime table -->
	 
<html:form action="/StoreEmployeecontract?task=storeOvertime">
	<c:if test="${employeeContractContext eq 'edit'}">
	<br>
	<br>
	<b><bean:message key="main.employeecontract.overtime.headline.text" /></b>
	<br>
	<br>
		<table class="center backgroundcolor">
			<tr>
				<td class="noBborderStyle" style="text-align: right">
					<b><bean:message key="main.employeecontract.overtime.total.text" />:</b>
				</td>
				<th style="text-align: right">
					<c:out value="${totalovertime}" />
				</th>
			</tr>
			<tr>
				<th>
					<b><bean:message
						key="main.employeecontract.overtime.date.text" /></b>
				</th>
				<th>
					<b><bean:message
						key="main.employeecontract.overtime.duration.text" /></b>
				</th>
				<th>
					<b><bean:message
						key="main.employeecontract.overtime.comment.text" /></b>
				</th>
			</tr>
			<c:forEach var="overtime" items="${overtimes}" varStatus="statusID">
				<c:choose>
					<c:when test="${statusID.count%2==0}">
						<tr class="primarycolor">
					</c:when>
					<c:otherwise>
						<tr class="secondarycolor">
					</c:otherwise>
				</c:choose>
					<td style="text-align: right">
						<c:out value="${overtime.createdString}" />
					</td>
					<td style="text-align: right">
						<java8:formatDuration value="${overtime.timeMinutes}" />
					</td>
					<td style="text-align: left">
						<c:out value="${overtime.comment}" />
					</td>
				</tr>	
			</c:forEach>
			<tr>
				<td style="text-align: right">
					<c:out value="${dateString}" />
				</td>
				<td style="text-align: right">
					<html:text property="newOvertime" size="10" style="text-align: right" />
				</td>					
				<td>
					<html:text property="newOvertimeComment" size="64" style="text-align: left" />
				</td>
				<td class="noBborderStyle">
					<html:submit styleId="button" styleClass="hiddencontent" titleKey="main.general.button.save.alttext.text">
						<bean:message key="main.general.button.save.text" />						
					</html:submit>
				</td>
			</tr>
			<tr>
				<td class="noBborderStyle" style="text-align: right">
					<b><bean:message key="main.employeecontract.overtime.total.text" />:</b>
				</td>
				<td style="text-align: right">
					<c:out value="${totalovertime}" />
				</td>
			</tr>
			
			<!-- error messages -->
			
			<tr>
				<td class="noBborderStyle" colspan="4">
					<span style="color:red"><html:errors property="newOvertime" /></span>
				</td>
			</tr>
			<tr>
				<td class="noBborderStyle" colspan="4">
					<span style="color:red"><html:errors property="newOvertimeComment" /></span>
				</td>
			</tr>		
		</table>
	</c:if>
</html:form>

</body>
</html:html>
