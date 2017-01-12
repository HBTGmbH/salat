<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
		<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.daily.text" /></title>
		<link rel="stylesheet" type="text/css" href="/tb/tb.css" media="all" />
		<link rel="stylesheet" type="text/css" href="/tb/print.css" media="print" />
		<link href="/tb/style/select2.min.css" rel="stylesheet" />
		<script src="/tb/scripts/jquery-1.11.3.min.js"></script>
		<script src="/tb/scripts/select2.full.min.js"></script>
		<script type="text/javascript" language="JavaScript">
			var failedMassEditIds = '${failedMassEditIds.toString()}';
			if(failedMassEditIds != "") {
				failedMassEditIds = JSON.parse(failedMassEditIds);
			}
		</script>
		<script src="/tb/scripts/massedit.js"></script>
		<script type="text/javascript" language="JavaScript">
		
		
	function afterCalenderClick() {
		document.forms[0].action = "/tb/do/ShowDailyReport?task=refreshTimereports";
		document.forms[0].submit();
	}			
		
	function changeDateAndUpdateTimereportsAction(form, date, change) {
		form.action = "/tb/do/ShowDailyReport?task=refreshTimereports&date=" + date + "&change=" + change;
		form.submit();
	}
		
 	function setUpdateTimereportsAction(form) {	
 		form.action = "/tb/do/ShowDailyReport?task=refreshTimereports";
		form.submit();
	}	
 
 	function setUpdateOrdersAction(form) {	
 		form.action = "/tb/do/ShowDailyReport?task=refreshOrders";
		form.submit();
	}	
	
	function setUpdateSubordersAction(form, id) {		
		alert('id: ' + id);
 		form.action = "/tb/do/ShowDailyReport?task=refreshSuborders&trId=" + id;
		form.submit();
	}		
	
	function printMyFormElement(form) {		
		alert('element: ' + form.elements['comment'].value);
	}	
	
	function createNewReportAction(form) {	
 		form.action = "/tb/do/CreateDailyReport";
		form.submit();
	}	
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreDailyReport?task=" + actionVal;
		form.submit();
	}
	
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteTimereportFromDailyDisplay?trId=" + id;
			form.submit();
		}
	}
	
	function submitUpdateDailyReport(form, id) {
		form.action = "/tb/do/UpdateDailyReport?trId=" + id;
		form.submit();
	}
	
	function confirmSave(form, id) {
		
		if (form.elements['status'] != null && form.elements['status'].value == 'closed') {
			var agree=confirm("<bean:message key="main.timereport.confirmclose.text" />");
			if (agree) {
				submitUpdateDailyReport(form, id);
			}
		} else {
			submitUpdateDailyReport(form, id);
		}
	}
	
	function saveBegin(form) {
		form.action = "/tb/do/ShowDailyReport?task=saveBegin";
		form.submit();
	}
	
	function saveBreak(form) {
		form.action = "/tb/do/ShowDailyReport?task=saveBreak";
		form.submit();
	}	
					
  	function setUpdateTimereportsAction(form) {	
 		form.action = "/tb/do/ShowDailyReport?task=refreshTimereports";
		form.submit();
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
	
	// textarea limitation
	function limitText(limitField, limitCount, limitNum) {
		if (limitField.value.length > limitNum) {
			limitField.value = limitField.value.substring(0, limitNum);
		} else {
			limitCount.value = limitNum - limitField.value.length;
		}
	}

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'element'
		});	
	});	
	
	var confirmMassDelete = '<bean:message key="main.general.confirmMassDelete.text" />';
	var cannotShiftReportsMsg = '<bean:message key="main.general.cannotShiftReports.text" />';
	</script>

	</head>
	
	<body>
		<jsp:include flush="true" page="/menu.jsp">
			<jsp:param name="title" value="Menu" />
		</jsp:include>
		<br>
		<span style="font-size: 14pt; font-weight: bold;">
			<br>
			<bean:message key="main.general.mainmenu.daily.text" />
			<br>
		</span>
		<br>
		<html:form action="/ShowDailyReport">
			<table class="center backgroundcolor">
				<colgroup>
					<col align="left" width="185" />
					<col align="left" width="750" />
				</colgroup>
				<!-- select employeecontract -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.employee.fullname.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle" nowrap="nowrap">
						<html:select property="employeeContractId" value="${currentEmployeeContract.id}" onchange="setUpdateTimereportsAction(this.form)" styleClass="make-select2">
							<c:if test="${not loginEmployee.restricted}">
								<html:option value="-1">
									<bean:message key="main.general.allemployees.text" />
								</html:option>
							</c:if>
							<c:forEach var="employeecontract" items="${employeecontracts}">
								<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
									<html:option value="${employeecontract.id}">
										<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" />
										<c:if test="${employeecontract.openEnd}">
											<bean:message key="main.general.open.text" />
										</c:if>)
									</html:option>
								</c:if>
							</c:forEach>
						</html:select>
					</td>
				</tr>
		
				<!-- select order -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.customerorder.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle" nowrap="nowrap">
						<html:select property="order" value="${currentOrder}" onchange="setUpdateTimereportsAction(this.form)" styleClass="make-select2">
							<html:option value="ALL ORDERS">
								<bean:message key="main.general.allorders.text" />
							</html:option>
							<html:options collection="orders" labelProperty="signAndDescription" property="sign" />
							<html:hidden property="orderId" />
						</html:select>
						<!-- select suborder -->
						<c:if test="${currentOrder != 'ALL ORDERS'}">
							<c:forEach var="order" items="${orders}">
								<c:if test="${order.sign == currentOrder}">
									/ 
									<html:select property="suborderId" onchange="setUpdateTimereportsAction(this.form)" value="${suborderFilerId}" styleClass="make-select2">
										<html:option value="-1">
											<bean:message key="main.general.allsuborders.text" />
										</html:option>
										<c:forEach var="suborder" items="${suborders}">
											<html:option value="${suborder.id}">
												<c:out value="${suborder.signAndDescription}"></c:out>
												<c:if test="${!suborder.currentlyValid}">
													&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
												</c:if>
											</html:option>
										</c:forEach>
									</html:select>
								</c:if>
							</c:forEach>
							<span style="font-size: 0.6em">
								<bean:message key="main.general.select.expired.text" />
							</span>
							<html:checkbox property="showOnlyValid" onclick="setUpdateTimereportsAction(this.form)" styleClass="middle-aligned">
								<span class="middle-aligned"><bean:message key="main.general.show.only.valid.text"/></span>
							</html:checkbox>
						</c:if>
						<c:if test="${currentOrder == 'ALL ORDERS'}">
							<html:checkbox property="showOnlyValid" onclick="setUpdateTimereportsAction(this.form)" style="display:none;" />
						</c:if>
					</td>
				</tr>
		
				<!-- select view mode -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.view.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="view" onchange="setUpdateTimereportsAction(this.form)">
							<html:option value="day">
								<bean:message key="main.general.timereport.view.daily.text" />
							</html:option>
							<html:option value="month">
								<bean:message key="main.general.timereport.view.monthly.text" />
							</html:option>
							<html:option value="custom">
								<bean:message key="main.general.timereport.view.custom.text" />
							</html:option>
							<html:hidden property="view" />
						</html:select>
					</td>
				</tr>
		
				<!-- select first date -->
				<tr>
					<c:choose>
						<c:when test="${view eq 'month'}">
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.monthyear.text" />:</b>
							</td>
						</c:when>
						<c:otherwise>
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.daymonthyear.text" /></b><c:if test="${view eq 'custom'}">&nbsp;<i>(<bean:message key="main.general.from.text" />)</i></c:if><b>:</b>
							</td>
						</c:otherwise>
					</c:choose>
		
					<td align="left" class="noBborderStyle">
						<!-- JavaScript Stuff for popup calender -->
						<c:choose>
							<c:when test="${!(view eq 'month')}">
								<script type="text/javascript" language="JavaScript" src="/tb/CalendarPopup.js">
								</script>
								<script type="text/javascript" language="JavaScript">
									document.write(getCalendarStyles());
								</script>
								<script type="text/javascript" language="JavaScript">
				                    function calenderPopupStartdate() {
				                        var cal = new CalendarPopup();
				                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
				                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
				                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
				                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
				                        cal.select(document.forms[0].startdate,'anchor1','yyyy-MM-dd');
				                    }
				                </script>
								<html:text property="startdate" onblur="setUpdateTimereportsAction(this.form)" readonly="false" size="10" maxlength="10" onkeydown="if(event.keyCode=13){setUpdateTimereportsAction(this.form);return false;}"/>
								<a href="javascript:calenderPopupStartdate()" name="anchor1" ID="anchor1" style="text-decoration: none;">
									<img src="/tb/images/popupcalendar.gif" width="22" height="22" alt='<bean:message key="main.date.popup.alt.text" />' style="border: 0; vertical-align: top">
								</a>
								<%-- Arrows for navigating the Date --%>
								&nbsp;&nbsp;
								<html:image src="/tb/images/pfeil_links.gif" style="border: 0; vertical-align: middle; height: 11px; width: 11px"
									onclick="changeDateAndUpdateTimereportsAction(this.form,'start','-1')" titleKey="main.date.popup.prevday">
								</html:image>
								&nbsp;&nbsp;
								<html:image src="/tb/images/pfeil_unten.gif" style="border: 0; vertical-align: middle; height: 11px; width: 11px"
									onclick="changeDateAndUpdateTimereportsAction(this.form,'start','0')" titleKey="main.date.popup.today">
								</html:image>
								&nbsp;&nbsp;
								<html:image src="/tb/images/pfeil_rechts.gif" style="border: 0; vertical-align: middle; height: 11px; width: 11px"
									onclick="changeDateAndUpdateTimereportsAction(this.form,'start','1')" titleKey="main.date.popup.nextday">
								</html:image>
							</c:when>
							<c:otherwise>
								<html:select property="month" value="<%=(String) request.getSession().getAttribute(\"currentMonth\")%>" 
									onchange="setUpdateTimereportsAction(this.form)">
									<html:option value="Jan">
										<bean:message key="main.timereport.select.month.jan.text" />
									</html:option>
									<html:option value="Feb">
										<bean:message key="main.timereport.select.month.feb.text" />
									</html:option>
									<html:option value="Mar">
										<bean:message key="main.timereport.select.month.mar.text" />
									</html:option>
									<html:option value="Apr">
										<bean:message key="main.timereport.select.month.apr.text" />
									</html:option>
									<html:option value="May">
										<bean:message key="main.timereport.select.month.may.text" />
									</html:option>
									<html:option value="Jun">
										<bean:message key="main.timereport.select.month.jun.text" />
									</html:option>
									<html:option value="Jul">
										<bean:message key="main.timereport.select.month.jul.text" />
									</html:option>
									<html:option value="Aug">
										<bean:message key="main.timereport.select.month.aug.text" />
									</html:option>
									<html:option value="Sep">
										<bean:message key="main.timereport.select.month.sep.text" />
									</html:option>
									<html:option value="Oct">
										<bean:message key="main.timereport.select.month.oct.text" />
									</html:option>
									<html:option value="Nov">
										<bean:message key="main.timereport.select.month.nov.text" />
									</html:option>
									<html:option value="Dec">
										<bean:message key="main.timereport.select.month.dec.text" />
									</html:option>
								</html:select>
								<html:select property="year" value="<%=(String) request.getSession().getAttribute(\"currentYear\")%>" 
									onchange="setUpdateTimereportsAction(this.form)">
									<html:options collection="years" property="value" labelProperty="label" />
								</html:select>
							</c:otherwise>
						</c:choose>
					</td>
				</tr>
		
				<!-- select second date -->
				<c:if test="${view eq 'custom'}">
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.monthlyreport.daymonthyear.text" /></b>&nbsp;<i>(<bean:message key="main.general.to.text" />)</i><b>:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<script type="text/javascript" language="JavaScript">
					                    function calenderPopupEnddate() {
					                        var cal = new CalendarPopup();
					                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
					                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
					                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
					                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
					                        cal.select(document.forms[0].enddate,'anchor2','yyyy-MM-dd');
					                    }
							</script>
							<html:text property="enddate" onblur="setUpdateTimereportsAction(this.form)" readonly="false" size="10" maxlength="10" />
							<a href="javascript:calenderPopupEnddate()" name="anchor2" ID="anchor2" style="text-decoration: none;">
								<img src="/tb/images/popupcalendar.gif" width="22" height="22" alt="<bean:message key="main.date.popup.alt.text" />" style="border: 0; vertical-align: top">
							</a> 
							<%-- Arrows for navigating the Date --%>
							&nbsp;&nbsp;
							<html:image src="/tb/images/pfeil_links.gif" style="border: 0; vertical-align: middle; height: 11px; width: 11px"
								onclick="changeDateAndUpdateTimereportsAction(this.form,'end','-1')" titleKey="main.date.popup.prevday">
							</html:image>
							&nbsp;&nbsp;
							<html:image src="/tb/images/pfeil_unten.gif" style="border: 0; vertical-align: middle; height: 11px; width: 11px"
								onclick="changeDateAndUpdateTimereportsAction(this.form,'end','0')" titleKey="main.date.popup.today">
							</html:image>
							&nbsp;&nbsp;
							<html:image src="/tb/images/pfeil_rechts.gif" style="border: 0; vertical-align: middle; height: 11px; width: 11px"
								onclick="changeDateAndUpdateTimereportsAction(this.form,'end','1')" titleKey="main.date.popup.nextday">
							</html:image>
						</td>
					</tr>
				</c:if>
		
				<!-- avoid refresh -->
				<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.avoidrefresh.text"/>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="avoidRefresh" onclick="setUpdateTimereportsAction(this.form)" />
					</td>
				</tr>
		
				<!-- show only project based training -->
				<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.showOnlyTraining.text"/>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="showTraining" onclick="setUpdateTimereportsAction(this.form)" />
					</td>
				</tr>
				
				<!-- compute overtime until chosen Date -->
	      		<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.overtimeUntilDate"/>:</b>
					</td>
					<c:choose>
	      				<c:when test="${overtimeDisabled=='true'}">
							<td align="left" class="noBborderStyle">
								<html:checkbox property="showOvertimeUntil" onclick="setUpdateTimereportsAction(this.form)" disabled="true"/>
							</td>
						</c:when>
						<c:otherwise>
							<td align="left" class="noBborderStyle">
								<html:checkbox property="showOvertimeUntil" onclick="setUpdateTimereportsAction(this.form)" />
							</td>
						</c:otherwise>
					</c:choose>
				</tr>
				
				<!-- seperator line -->
				<tr>
					<td width="100%" class="noBborderStyle" colspan="2">
						<hr>
					</td>
				</tr>
		
				<!-- select working day begin and  break -->
				<c:if test="${view eq 'day' || view == null}">
					<c:if test="${currentEmployee != 'ALL EMPLOYEES'}">
						<tr>
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.timereport.startofwork.text" /></b> <i>(hh:mm)</i><b>:</b>
							</td>
							<td align="left" class="noBborderStyle">
								<nobr>
									<html:select property="selectedWorkHourBegin">
										<html:options collection="hours" property="value" labelProperty="label" />
									</html:select>
									<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
									<html:select property="selectedWorkMinuteBegin">
										<html:options collection="minutes" property="value" labelProperty="label" />
									</html:select>
									&nbsp;&nbsp;
									<html:image onclick="saveBegin(this.form)" src="/tb/images/Save.gif" alt="save start of work" />
									&nbsp;&nbsp; <i>(optional)</i>
								</nobr>
							</td>
						</tr>
		
						<%-- is a visible, when workingday null --%>
						<c:if test="${visibleworkingday}">
							<tr>
								<td align="left" class="noBborderStyle">
									<b><bean:message key="main.timereport.breakduration.text" /></b> <i>(hh:mm)</i><b>:</b>
								</td>
								<td align="left" class="noBborderStyle">
									<html:select property="selectedBreakHour">
										<html:options collection="breakhours" property="value" labelProperty="label" />
									</html:select>
									<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b> 
									<html:select property="selectedBreakMinute">
										<html:options collection="breakminutes" property="value" labelProperty="label" />
									</html:select>
									&nbsp;&nbsp; 
									<html:image onclick="saveBreak(this.form)" src="/tb/images/Save.gif" alt="save break" />
									&nbsp;&nbsp;<i>(optional)</i>
								</td>
							</tr>
							<tr>
								<td align="left" class="noBborderStyle">
									<b><bean:message key="main.timereport.quittingtime.text" />:</b>
								</td>
								<td align="left" class="noBborderStyle">
									<b><c:out value="${quittingtime}"></c:out></b>
								</td>
							</tr>
							<tr>
								<td align="left" class="noBborderStyle">
									<b><bean:message key="main.timereport.workingdayends.text" />:</b>
								</td>
								<td align="left" class="noBborderStyle">
									<b><c:out value="${workingDayEnds}"></c:out></b>
								</td>
							</tr>
						</c:if>
					</c:if>
				</c:if>
			</table>
		</html:form>
	
		<bean:size id="timereportsSize" name="timereports" />
		<c:if test="${timereportsSize>10}">
			<table>
				<tr>
					<html:form action="/CreateDailyReport">
						<td class="noBborderStyle" colspan="6" align="left">
							<html:submit styleId="button" titleKey="main.general.button.createnewreport.alttext.text">
								<bean:message key="main.general.button.createnewreport.text" />
							</html:submit>
						</td>
					</html:form>
					<html:form target="fenster"	onsubmit="window.open('','fenster','width=800,height=400,resizable=yes')" action="/ShowDailyReport?task=print">
						<td class="noBborderStyle" colspan="6" align="left">
							<html:submit styleId="button" titleKey="main.general.button.printpreview.alttext.text">
								<bean:message key="main.general.button.printpreview.text" />
							</html:submit>
						</td>
					</html:form>
				</tr>
			</table>
		</c:if>
		
		<c:if test="${vacationBudgetOverrun}">
			<table>
				<td  class="noBborderStyle"  style="font-size: 14pt; ">
					<b><font color="red"><bean:message key="form.timereport.error.vacationBudgetOverrun" /></font></b>
				</td>
			</table>
		</c:if>
	
		<table class="center backgroundcolor" width="100%">
			<tr>
				<td colspan="6" class="noBborderStyle">
					&nbsp;
				</td>
				<td class="noBborderStyle" align="right">
					<b><bean:message key="main.timereport.total.text" />:</b>
				</td>
				<c:choose>
					<c:when test="${maxlabortime && view eq 'day' && !(currentEmployee eq 'ALL EMPLOYEES')}">
						<th align="center" color="red">
							<b><font color="red"><c:out	value="${labortime}"></c:out></font></b>
						</th>
					</c:when>
					<c:otherwise>
						<th align="center">
							<b><c:out value="${labortime}"></c:out></b>
						</th>
					</c:otherwise>
				</c:choose>
				<th align="center">
					<b><fmt:formatNumber value="${dailycosts}" minFractionDigits="2" /></b>
				</th>
			</tr>
		
			<tr>
				<th align="left">
					<b>Info</b>
				</th>
				<th align="left" title="<bean:message key='main.headlinedescription.dailyoverview.employee.text' />">
					<html:link href="/tb/do/ShowDailyReport?task=sort&column=employee">
						<b><bean:message key="main.timereport.monthly.employee.sign.text" /></b>
					</html:link>
					<c:if test="${timereportSortColumn eq 'employee'}">
						<c:out value="${timereportSortModus}" />
					</c:if>
				</th>
				<th align="left" title="<bean:message key='main.headlinedescription.dailyoverview.refday.text' />">
					<html:link href="/tb/do/ShowDailyReport?task=sort&column=refday">
						<b><bean:message key="main.timereport.monthly.refday.text" /></b>
					</html:link>
					<c:if test="${timereportSortColumn eq 'refday'}">
						<c:out value="${timereportSortModus}" />
					</c:if>
				</th>
				<%--
						<th align="left" title="<bean:message key='main.headlinedescription.dailyoverview.sortofreport.text' />">
							<b><bean:message key="main.timereport.monthly.sortofreport.text" /></b>
						</th>
				--%>
				<th align="left" title="<bean:message key='main.headlinedescription.dailyoverview.customerorder.text' />">
					<html:link href="/tb/do/ShowDailyReport?task=sort&column=order">
						<b><bean:message key="main.timereport.monthly.customerorder.text" /></b>
					</html:link>
					<c:if test="${timereportSortColumn eq 'order'}">
						<c:out value="${timereportSortModus}" />
					</c:if>
				</th>
				<th align="left" title="<bean:message key='main.headlinedescription.dailyoverview.description.text' />">
					<b><bean:message key="main.customerorder.shortdescription.text" /></b>
				</th>
				<th align="left" title="<bean:message key='main.headlinedescription.dailyoverview.taskdescription.text' />" width="25%">
					<b><bean:message key="main.timereport.monthly.taskdescription.text" /></b>
				</th>
				<th align="center" title="<bean:message key='main.headlinedescription.dailyoverview.training.text' />">
					<b><bean:message key="main.timereport.monthly.training.text" /></b>
				</th>
				<th align="center" title="<bean:message key='main.headlinedescription.dailyoverview.hours.text' />">
					<b><bean:message key="main.timereport.monthly.hours.text" /></b>
				</th>
				<th align="center" title="<bean:message	key="main.headlinedescription.dailyoverview.costs.text" />">
					<b><bean:message key="main.timereport.monthly.costs.text" /></b>
				</th>
				<th align="center" title="<bean:message	key='main.headlinedescription.dailyoverview.saveeditdelete.text' />">
					<b><bean:message key="main.timereport.monthly.saveeditdelete.text" /></b>
				</th>
			</tr>
		
			<c:forEach var="timereport" items="${timereports}" varStatus="statusID">
				<html:form action="/UpdateDailyReport?trId=${timereport.id}">
					<c:choose>
						<c:when test="${statusID.count%2 == 0}">
							<tr class="primarycolor">
						</c:when>
						<c:otherwise>
							<tr class="secondarycolor">
						</c:otherwise>
					</c:choose>
		
					<!-- Info -->
					<td align="center">
						<div class="tooltip" id="info<c:out value='${timereport.id}'/>">
							<table>
								<tr>
									<td class="info">id:</td>
									<td class="info" colspan="3">
										<c:out value="${timereport.id}" />
									</td>
								</tr>
								<tr>
									<td class="info"><bean:message key="main.timereport.tooltip.employee" />:</td>
									<td class="info" colspan="3">
										<c:out value="${timereport.employeecontract.employee.name}" />
										&nbsp;&nbsp;(<c:out	value="${timereport.employeecontract.timeString}" />
										<c:if test="${employeecontract.openEnd}">
											<bean:message key="main.general.open.text" />
										</c:if>)
									</td>
								</tr>
								<tr>
									<td class="info"><bean:message key="main.timereport.tooltip.order" />:</td>
									<td class="info" colspan="3">
										<c:out	value="${timereport.suborder.customerorder.sign}" />
									</td>
								</tr>
								<tr>
									<td class="info">&nbsp;</td>
									<td class="info" colspan="3">
										<c:out	value="${timereport.suborder.customerorder.description}" />
									</td>
								</tr>
								<tr>
									<td class="info"><bean:message key="main.timereport.tooltip.suborder" />:</td>
									<td class="info" colspan="3">
										<c:out value="${timereport.suborder.sign}" />
									</td>
								</tr>
								<tr>
									<td class="info">&nbsp;</td>
									<td class="info" colspan="3">
										<c:out value="${timereport.suborder.description}" />
									</td>
								</tr>
								<tr>
									<td class="info"><bean:message key="main.timereport.tooltip.status" />:</td>
									<td class="info">
										<c:out value="${timereport.status}" />
									</td>
								</tr>
								<tr>
									<td class="info" valign="top"><bean:message key="main.timereport.tooltip.created" />:</td>
									<td class="info">
										<fmt:formatDate pattern="yyyy-MM-dd HH:mm:ss" value="${timereport.created}" />
									</td>
									<td class="info" valign="top"><bean:message key="main.timereport.tooltip.by" /></td>
									<td class="info" valign="top">
										<c:out value="${timereport.createdby}" />
									</td>
								</tr>
								<tr>
									<td class="info" valign="top"><bean:message key="main.timereport.tooltip.edited" />:</td>
									<td class="info">
										<fmt:formatDate pattern="yyyy-MM-dd HH:mm:ss" value="${timereport.lastupdate}" />
									</td>
									<td class="info" valign="top"><bean:message key="main.timereport.tooltip.by" /></td>
									<td class="info" valign="top">
										<c:out value="${timereport.lastupdatedby}" />
									</td>
								</tr>
								<tr>
									<td class="info" valign="top"><bean:message	key="main.timereport.tooltip.released" />:</td>
									<td class="info">
										<c:out value="${timereport.released}" />
									</td>
									<td class="info" valign="top"><bean:message key="main.timereport.tooltip.by" /></td>
									<td class="info" valign="top">
										<c:out value="${timereport.releasedby}" />
									</td>
								</tr>
								<tr>
									<td class="info" valign="top"><bean:message	key="main.timereport.tooltip.accepted" />:</td>
									<td class="info">
										<c:out value="${timereport.accepted}" />
									</td>
									<td class="info" valign="top"><bean:message	key="main.timereport.tooltip.by" /></td>
									<td class="info" valign="top">
										<c:out value="${timereport.acceptedby}" />
									</td>
								</tr>
							</table>
						</div>
						<img onMouseOver="showWMTT(this,'info<c:out value="${timereport.id}" />')" 
							onMouseOut="hideWMTT()" width="12px" height="12px" src="/tb/images/info_button.gif" />
						<c:if test="${!timereport.fitsToContract}">
							<img width="20px" height="20px" src="/tb/images/Pin rot.gif" title="<bean:message key='main.timereport.warning.datedoesnotfit' />" />
						</c:if>
					</td>
		
					<!-- Mitarbeiter -->
					<td title="<c:out value='${timereport.employeecontract.employee.name}' />&nbsp;&nbsp;(<c:out value='${timereport.employeecontract.timeString}' />
						<c:if test="${employeecontract.openEnd}">
							<bean:message key="main.general.open.text" />
						</c:if>)">
						<c:out value="${timereport.employeecontract.employee.sign}" />
					</td>
		
					<!-- Datum -->
					<td title='<c:out value="${timereport.referenceday.name}" />'>
						<logic:equal name="timereport" property="referenceday.holiday" value="true">
							<span style="color: red">
								<bean:message key="${timereport.referenceday.dow}" />
								<br>
								<c:out value="${timereport.referenceday.refdate}" />
							</span>
						</logic:equal>
						<logic:equal name="timereport" property="referenceday.holiday" value="false">
							<bean:message key="${timereport.referenceday.dow}" />
							<br>
							<c:out value="${timereport.referenceday.refdate}" />
						</logic:equal>
					</td>
					<!-- Auftrag -->
					<td	title="<c:out value='${timereport.suborder.customerorder.description}' />">
						<c:out value="${timereport.suborder.customerorder.sign}" />
						<br>
						<c:out value="${timereport.suborder.sign}" />
					</td>
		
					<!-- Bezeichnung -->
					<td>
						<c:out value="${timereport.suborder.customerorder.shortdescription}" />
						<br>
						<c:out value="${timereport.suborder.shortdescription}" />
					</td>
		
					<!-- visibility dependent on user and status -->
					<c:choose>
						<c:when	test="${((loginEmployee == timereport.employeecontract.employee) && (timereport.status eq 'open')) || ((loginEmployee.status eq 'bl' || loginEmployee.status eq 'pv') && (timereport.status eq 'commited') && (loginEmployee != timereport.employeecontract.employee)) || loginEmployee.status eq 'adm'}">
							<!-- Kommentar -->
							<td>
								<html:textarea property="comment" cols="30" rows="1" value="${timereport.taskdescription}" 
									onkeydown="limitText(this.form.comment,this.form.countdown,256);"
									onkeyup="limitText(this.form.comment,this.form.countdown,256);" 
									styleClass="showDailyReport" />
							</td>
							
							<!-- Fortbildung -->
							<td align="center">
								<input type="checkbox" name="training" ${timereport.training ? 'checked' : '' } />  
							</td>
		
							<!-- Dauer -->
							<td align="center" nowrap="nowrap">
								<html:select name="timereport" property="selectedDurationHour" value="${timereport.durationhours}" disabled="${timereport.suborder.sign eq overtimeCompensation}">
									<html:options collection="hoursDuration" property="value" labelProperty="label" />
								</html:select>
								<html:select property="selectedDurationMinute" value="${timereport.durationminutes}" disabled="${timereport.suborder.sign eq overtimeCompensation}">
									<html:options collection="minutes" property="value"	labelProperty="label" />
								</html:select>
							</td>
		
							<!-- Kosten -->
							<td align="center">
								<html:text property="costs" size="4" value="${timereport.costs}" />
							</td>
							<!-- Bearbeiten -->
							<td align="center">
								<html:image	onclick="confirmSave(this.form, ${timereport.id})" src="/tb/images/Save.gif" alt="Speichern" title="Speichern" />
								&nbsp;
								<html:link title="Ändern" href="/tb/do/EditDailyReport?trId=${timereport.id}">
									<img src="/tb/images/Edit.gif" alt="Ändern" />
								</html:link>
								&nbsp;
								<html:image	onclick="confirmDelete(this.form, ${timereport.id})" src="/tb/images/Delete.gif" alt="Löschen" title="Löschen" />
								&nbsp;
								<span id="span-massedit-${timereport.id}">
									<input type="checkbox" class="massedit" title='<bean:message key="main.timereport.tooltip.mass.edit" />' alt='<bean:message key="main.timereport.tooltip.mass.edit" />' id="massedit_${timereport.id}" onchange="HBT.MassEdit.onChangeHandler(this)" />
								</span>
							</td>
						</c:when>
						<c:otherwise>
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
							<!-- Fortbildung -->
							<td align="center">
								<input type="checkbox" name="training" ${timereport.training ? 'checked' : '' } />    
							</td>
							<!-- Dauer -->
							<td align="center" nowrap>
								<c:if test='${timereport.durationhours < 10}'>0</c:if><c:out value="${timereport.durationhours}" />:<c:if test='${timereport.durationminutes < 10}'>0</c:if><c:out value="${timereport.durationminutes}" />
							</td>
							<!-- Kosten -->
							<td align="center">
								<fmt:formatNumber value="${timereport.costs}" minFractionDigits="2" />
							</td>
							<!-- Bearbeiten -->
							<td align="center">
								<img width="12px" height="12px"	src="/tb/images/verbot.gif" alt="Delete Timereport" />
							</td>
						</c:otherwise>
					</c:choose>
				</html:form>
			</c:forEach>
			<tr>
				<td colspan="6" class="noBborderStyle">
					&nbsp;
				</td>
				<td class="noBborderStyle" align="right">
					<b><bean:message key="main.timereport.total.text" />:</b>
				</td>
				<c:choose>
					<c:when test="${maxlabortime && view eq 'day' && !(currentEmployee eq 'ALL EMPLOYEES')}">
						<th align="center" color="red">
							<b><font color="red"><c:out	value="${labortime}"></c:out></font></b>
						</th>
					</c:when>
					<c:otherwise>
						<th align="center">
							<b><c:out value="${labortime}"></c:out></b>
						</th>
					</c:otherwise>
				</c:choose>
				<th align="center">
					<b><fmt:formatNumber value="${dailycosts}" minFractionDigits="2" /></b>
				</th>
				<td class="massedit invisible" align="center">
					<html:form action="/ShowDailyReport" style="margin-bottom:0">
						<b><bean:message key="main.timereport.mass.edit.text" />:</b><br />
						<html:image	onclick="return HBT.MassEdit.confirmDelete(this.form, confirmMassDelete);" src="/tb/images/Delete.gif" alt="Löschen" title="Löschen" />
						&nbsp;&nbsp;
						<div class="massedit-time-shift-dropdown">
							<img class="dropdown-btn" height="11" src="/tb/images/Button/backwards.svg" title="Um Tage Verschieben" alt="Um Tage verschieben" onclick="$('.dropdown-content').focus()"/>
							<div class="dropdown-content" tabindex="1">
								<table>
									<tr><th colspan="2"><bean:message key="main.timereport.mass.edit.shift.days.text" /></th></tr>
									<c:forEach begin="1" end="7" varStatus="loop">
										<tr><td><span title='<bean:message key="main.timereport.mass.edit.shift.days.tooltip" arg0='-${loop.index}' />' onclick='return HBT.MassEdit.confirmShiftDays(this, "<bean:message key="main.timereport.mass.edit.shift.days.confirm.msg" arg0='-${loop.index}' />", -${loop.index});'>-${loop.index}</span></td>
										    <td><span title='<bean:message key="main.timereport.mass.edit.shift.days.tooltip" arg0='+${loop.index}' />' onclick='return HBT.MassEdit.confirmShiftDays(this, "<bean:message key="main.timereport.mass.edit.shift.days.confirm.msg" arg0='+${loop.index}' />", ${loop.index});'>+${loop.index}</span></td></tr>
									</c:forEach>
								</table>
							</div>
						</div>
					</html:form>	
				</td>
			</tr>
		</table>
		<table>
			<tr>
				<html:form action="/CreateDailyReport">
					<td class="noBborderStyle" colspan="6" align="left">
						<html:submit styleId="button" titleKey="main.general.button.createnewreport.alttext.text">
							<bean:message key="main.general.button.createnewreport.text" />
						</html:submit>
					</td>
				</html:form>
				<html:form target="fenster"	onsubmit="window.open('','fenster','width=800,height=400,resizable=yes')" action="/ShowDailyReport?task=print">
					<td class="noBborderStyle" colspan="6" align="left">
						<html:submit styleId="button" titleKey="main.general.button.printpreview.alttext.text">
							<bean:message key="main.general.button.printpreview.text" />
						</html:submit>
					</td>
				</html:form>
			</tr>
		</table>
		<br>
	
		<span style="color: red">
			<b>
				<html:errors property="trSuborderId" footer="<br>" />
				<html:errors property="selectedHourEnd" footer="<br>" /> 
				<html:errors property="costs" footer="<br>" />
				<html:errors property="status" footer="<br>" />
				<html:errors property="createWorklogFailed" footer="<br>" />
				<br>
				<html:errors property="comment" footer="<br>" />
			</b>
		</span>
	
		<!-- Überstunden und Urlaubstage -->
		<c:choose>
			<c:when test="${currentEmployee != 'ALL EMPLOYEES'}">
				<br><br><br>
				<jsp:include flush="true" page="/info2.jsp">
					<jsp:param name="info" value="Info" />
				</jsp:include>
			</c:when>
			<c:otherwise>
				<br><br><br><br><br><br><br><br><br><br><br>
			</c:otherwise>
		</c:choose>
	</body>
</html:html>
