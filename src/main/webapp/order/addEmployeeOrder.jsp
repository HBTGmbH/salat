<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%><%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib prefix="java8" uri="http://hbt.de/jsp/taglib/java8-date-formatting" %>
<html:html>
<head>

<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addemployeeorder.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
<script type="text/javascript" language="JavaScript">
	
	function setDate(which, howMuch) {
		document.forms[0].action = "/do/StoreEmployeeorder?task=setDate&which=" + which + "&howMuch=" + howMuch;
		document.forms[0].submit();
	}
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/do/StoreEmployeeorder?task=" + actionVal + "&continue=" + addMore;
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

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'auto'
		});	
	});		
</script>

</head>
<body>
<html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />
<html:form action="/StoreEmployeeorder">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;">
		<br>
		<c:choose>
			<c:when test="${newemployeeorder}">
				<bean:message key="main.employeeorder.new.text" />:
			</c:when>
			<c:otherwise>
				<bean:message key="main.employeeorder.modify.text" />:
			</c:otherwise>
		</c:choose>
		<br>
	</span>
	<br>
	<span style="color:red">
		<html:errors property="overleap" />
	</span>
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">
		<colgroup>
			<col align="left" width="150" />
			<col align="left" width="750" />
		</colgroup>
		<!-- select employee contract -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.employee.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="employeeContractId" onchange="setStoreAction(this.form, 'refreshEmployee')" styleClass="make-select2">
					<c:forEach var="employeecontract" items="${employeecontracts}" >
							<html:option value="${employeecontract.id}">
								<c:out value="${employeecontract.employee.name}" /> |
								<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
									value="${employeecontract.timeString}" />
								<c:if test="${employeecontract.openEnd}">
									<bean:message key="main.general.open.text" />
								</c:if>)
							</html:option>
					</c:forEach>
				</html:select>
			</td>
		</tr>
		<!-- Auftrag -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.customerorder.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="orderId" onchange="setStoreAction(this.form, 'refreshSuborders')" styleClass="make-select2">
					<html:options collection="orderswithsuborders" labelProperty="signAndDescription" property="id" />
				</html:select>
				<span style="color:red">
					<html:errors property="orderId" />
				</span>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				&nbsp;&nbsp;&nbsp;<bean:message key="main.employeeorder.customerorder.description.text" />:
			</td>
			<td align="left" class="noBborderStyle">
				<i><c:out value="${selectedcustomerorder.description}"/></i>
			</td>
		</tr>
		<!-- Unterauftrag -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.suborder.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="suborderId" styleClass="mandatory make-select2" onchange="setStoreAction(this.form, 'refreshSuborderDescription')">
					<c:forEach var="suborder" items="${suborders}">
						<html:option value="${suborder.id}">
							<c:out value="${suborder.signAndDescription}"/>
							<c:if test="${!suborder.currentlyValid}">
								&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
							</c:if>
						</html:option>
					</c:forEach>
				</html:select>
				<c:if test="${!selectedsuborder.currentlyValid}">
					<span style="font-size: 0.6em">
						<bean:message key="main.general.select.expired.text"/>				
					</span>
				</c:if>
				<html:checkbox property="showOnlyValid" onclick="setStoreAction(this.form, 'refreshSuborders')" styleClass="middle-aligned">
					<span class="middle-aligned"><bean:message key="main.general.show.only.valid.text"/></span>
				</html:checkbox>
				<span style="color:red">
					<html:errors property="suborderId" />
				</span>
				<html:hidden property="suborderId" />
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				&nbsp;&nbsp;&nbsp;<bean:message key="main.employeeorder.suborder.description.text" />:
			</td>
			<td align="left" class="noBborderStyle">
				<c:if test="${selectedsuborder != null}">
					<i><c:out value="${selectedsuborder.description}"/></i>
				</c:if>
			</td>
		</tr>
		<!-- Gültig ab -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.validfrom.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<!-- JavaScript Stuff for popup calender -->
				<script type="text/javascript" language="JavaScript" src="/scripts/CalendarPopup.js">
				</script>
				<script type="text/javascript" language="JavaScript">
                    document.write(getCalendarStyles());
                </script>
                <script type="text/javascript" language="JavaScript">
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
                </script>
                <html:text property="validFrom" readonly="false" size="10" maxlength="10" />
                <a href="javascript:calenderPopupFrom()" name="from" ID="from" style="text-decoration:none;">
                	<img src="<c:url value="/images/popupcalendar.gif"/>" width="22" height="22" alt="<bean:message key="main.date.popup.alt.text" />" style="border:0;vertical-align:top">
                </a>
				<%-- Arrows for navigating the from-Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','-1')" title="<bean:message key="main.date.popup.prevday" />">
					<img src="<c:url value="/images/pfeil_links.gif"/>" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','0')" title="<bean:message key="main.date.popup.today" />">
					<img src="<c:url value="/images/pfeil_unten.gif"/>" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','1')" title="<bean:message key="main.date.popup.nextday" />">
					<img src="<c:url value="/images/pfeil_rechts.gif"/>" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				<span style="color:red">
					<html:errors property="validFrom" />
				</span>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				&nbsp;&nbsp;&nbsp;<bean:message key="main.employeeorder.suborder.text" />:
			</td>
			<td align="left" class="noBborderStyle">
				<i><c:out value="${selectedsuborder.fromDate}" /></i>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				&nbsp;&nbsp;&nbsp;<bean:message key="main.employeeorder.employee.text" />:
			</td>
			<td align="left" class="noBborderStyle">
				<i><c:out value="${currentEmployeeContract.validFrom}" /></i>
			</td>
		</tr>
		<!-- Gültig bis -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.validuntil.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:text property="validUntil" readonly="false" size="10" maxlength="10" />
				<a href="javascript:calenderPopupUntil()" name="until" ID="until" style="text-decoration:none;">
					<img src="<c:url value="/images/popupcalendar.gif"/>" width="22" height="22" alt="<bean:message key="main.date.popup.alt.text" />" style="border:0;vertical-align:top">
				</a>
				<%-- Arrows for navigating the until-Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','-1')" title="<bean:message key="main.date.popup.prevday" />">
					<img src="<c:url value="/images/pfeil_links.gif"/>" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','0')" title="<bean:message key="main.date.popup.today" />">
					<img src="<c:url value="/images/pfeil_unten.gif"/>" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','1')" title="<bean:message key="main.date.popup.nextday" />">
					<img src="<c:url value="/images/pfeil_rechts.gif"/>" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				<span style="color:red">
					<html:errors property="validUntil" />
				</span>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				&nbsp;&nbsp;&nbsp;<bean:message key="main.employeeorder.suborder.text" />:
			</td>
			<td align="left" class="noBborderStyle">
				<i>
					<c:out value="${selectedsuborder.untilDate}" />
					<c:if test="${selectedsuborder.untilDate == null}">
						<bean:message key="main.general.open.text" />
					</c:if>
				</i>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				&nbsp;&nbsp;&nbsp;<bean:message key="main.employeeorder.employee.text" />:
			</td>
			<td align="left" class="noBborderStyle">
				<i>
					<c:out value="${currentEmployeeContract.validUntil}" />
					<c:if test="${currentEmployeeContract.validUntil == null}">
						<bean:message key="main.general.open.text" />
					</c:if>
				</i>
			</td>
		</tr>
		<!-- Sollstunden -->
		<c:if test="${authorizedUser.admin || (!(selectedcustomerorder.sign eq 'URLAUB' || selectedcustomerorder.sign eq 'KRANK'))}">
			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.general.debithours.text" />:</b>
				</td>
				<td align="left" class="noBborderStyle" colspan="5">
					<html:text property="debithours" size="20" />
					<span style="color:red">
						<html:errors property="debithours" />
					</span>
				</td>
			<tr>
			<tr>
				<td align="left" class="noBborderStyle">
					&nbsp;&nbsp;&nbsp;<bean:message key="main.general.per.text"/>
				</td>
				<td align="left" class="noBborderStyle">
					<html:radio property="debithoursunit" value="12" disabled="false" />
					<bean:message key="main.general.month.text" />
					<html:radio	property="debithoursunit" value="1" disabled="false" />
					<bean:message key="main.general.year.text" />
					<html:radio	property="debithoursunit" value="0" disabled="false" />
					<bean:message key="main.general.totaltime.text" />
				</td>
			</tr>
		</c:if>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle">
				<html:submit onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button" titleKey="main.general.button.save.alttext.text">
					<bean:message key="main.general.button.save.text" />
				</html:submit>
			</td>
			<c:if test="${newemployeeorder}">
				<td class="noBborderStyle">
					<html:submit onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button" titleKey="main.general.button.saveandcontinue.alttext.text">
						<bean:message key="main.general.button.saveandcontinue.text" />
					</html:submit>
				</td>
			</c:if>
			<td class="noBborderStyle">
				<html:submit onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button" titleKey="main.general.button.reset.alttext.text">
					<bean:message key="main.general.button.reset.text" />
				</html:submit>
			</td>
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
