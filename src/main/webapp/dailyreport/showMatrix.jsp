<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<html>
<head>

	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<title>
		<bean:message key="main.general.application.title" /> -
		<bean:message key="main.general.mainmenu.matrix.title.text" />
	</title>
	
	<link rel="stylesheet" type="text/css" href="/style/tb.css" media="all" />
	<link rel="stylesheet" type="text/css" href="/style/print.css" media="print" />
	<link href="/style/select2.min.css" rel="stylesheet" />
	<link rel="shortcut icon" type="image/x-icon" href="/favicon.ico" />
	<script src="/scripts/jquery-1.11.3.min.js"></script>
	<script src="/scripts/select2.full.min.js"></script>
	
	<script type="text/javascript" language="JavaScript">
		function setUpdateMergedreportsAction(form) {
			form.action = "/do/ShowMatrix?task=refreshMergedreports";
			form.submit();
		}

		function setMonth(mode) {
			document.forms[0].action = "/do/ShowMatrix?task=setMonth&mode=" + mode;
			document.forms[0].submit();
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
		<span style="font-size: 14pt; font-weight: bold;"><br><bean:message key="main.general.mainmenu.matrix.text" /><br></span>
	<br>
	<html:form action="/ShowMatrix">
		<table class="center backgroundcolor">
			<!-- select employee -->
			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.monthlyreport.employee.fullname.text" />:</b>
				</td>
				<td align="left" class="noBborderStyle"><html:select
						property="employeeContractId"
						value="${currentEmployeeContract.id}"
						onchange="setUpdateMergedreportsAction(this.form)"
						styleClass="make-select2">
						<c:if test="${not loginEmployee.restricted}">
							<html:option value="-1">
								<bean:message key="main.general.allemployees.text" />
							</html:option>
						</c:if>

						<c:forEach var="employeecontract" items="${employeecontracts}">
							<c:if
								test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
								<html:option value="${employeecontract.id}">
									<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
										value="${employeecontract.timeString}" />
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
				<td align="left" class="noBborderStyle"><html:select
						property="order"
						value="<%=(String) request.getSession().getAttribute(\"currentOrder\")%>"
						onchange="setUpdateMergedreportsAction(this.form)"
						styleClass="make-select2">

						<html:option value="ALL ORDERS">
							<bean:message key="main.general.allorders.text" />
						</html:option>

						<html:options collection="orders"
							labelProperty="signAndDescription" property="sign" />
						<html:hidden property="orderId" />
					</html:select></td>
			</tr>

			<!-- select first date -->
			<tr>
				<c:choose>
					<c:when test="${matrixview eq 'month'}">
						<td align="left" class="noBborderStyle"><b><bean:message
									key="main.monthlyreport.monthyear.text" />:</b></td>
					</c:when>
					<c:otherwise>
						<td align="left" class="noBborderStyle"><b><bean:message
									key="main.monthlyreport.daymonthyear.text" />:</b></td>
					</c:otherwise>
				</c:choose>

				<td align="left" class="noBborderStyle"><c:if
						test="${!(matrixview eq 'month')}">
						<html:select property="fromDay" value="${currentDay}"
							onchange="setUpdateMergedreportsAction(this.form)" styleClass="make-select2">
							<html:options collection="days" property="value"
								labelProperty="label" />
						</html:select>
					</c:if> <html:select property="fromMonth" value="${currentMonth}"
						onchange="setUpdateMergedreportsAction(this.form)" styleClass="make-select2">
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
					</html:select> <html:select property="fromYear" value="${currentYear}"
						onchange="setUpdateMergedreportsAction(this.form)" styleClass="make-select2">
						<html:options collection="years" property="value"
							labelProperty="label" />
					</html:select>

					<c:if test="${matrixview != 'custom'}">
						<%-- Arrows for navigating the month --%>

						<a href="javascript:setMonth('-1')" style="margin-left: 10px">
							<img src="/images/pfeil_links.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
						</a>

						<a href="javascript:setMonth('0')" style="margin-left: 10px">
							<img src="/images/pfeil_unten.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
						</a>

						<a href="javascript:setMonth('1')" style="margin-left: 10px">
							<img src="/images/pfeil_rechts.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
						</a>
					</c:if>
				</td>
			</tr>

			<!-- select second date -->
			<c:if test="${matrixview eq 'custom'}">
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
								key="main.monthlyreport.daymonthyear.text" />:</b></td>
					<td align="left" class="noBborderStyle"><html:select
							property="untilDay" value="${lastDay}"
							onchange="setUpdateMergedreportsAction(this.form)" styleClass="make-select2">
							<html:options collection="days" property="value"
								labelProperty="label" />
						</html:select> <html:select property="untilMonth" value="${lastMonth}"
							onchange="setUpdateMergedreportsAction(this.form)" styleClass="make-select2">
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
						</html:select> <html:select property="untilYear" value="${lastYear}"
							onchange="setUpdateMergedreportsAction(this.form)" styleClass="make-select2">
							<html:options collection="years" property="value"
								labelProperty="label" />
						</html:select></td>
				</tr>
			</c:if>
			<!-- select invoice -->
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message key="main.monthlyreport.invoice.text" />:</b></td>
				<td align="left" class="noBborderStyle"><html:checkbox property="invoice" onclick="setUpdateMergedreportsAction(this.form)" /></td>
			</tr>
			<!-- select invoice -->
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message key="main.monthlyreport.non.invoice.text" />:</b></td>
				<td align="left" class="noBborderStyle"><html:checkbox property="nonInvoice" onclick="setUpdateMergedreportsAction(this.form)" /></td>
			</tr>
		</table>
	</html:form>

	<bean:size id="mergedreportsSize" name="mergedreports" />
	<c:if test="${mergedreportsSize>10}">
		<table>
			<tr>
				<c:if
					test="${(loginEmployee.name == currentEmployee) || loginEmployee.id == currentEmployeeId || loginEmployee.status eq 'bl' || loginEmployee.status eq 'pv'|| loginEmployee.status eq 'adm'}">
					<html:form action="/CreateDailyReport?task=matrix">
						<td class="noBborderStyle" align="left"><html:submit
								styleId="button"
								titleKey="main.general.button.createnewreport.alttext.text">
								<bean:message key="main.general.button.createnewreport.text" />
							</html:submit></td>
					</html:form>
				</c:if>
				<html:form target="fenster"
					onsubmit="window.open('','fenster','width=800,height=400,resizable=yes')"
					action="/ShowMatrix?task=print">
					<td class="noBborderStyle" align="left"><html:submit
							styleId="button"
							titleKey="main.general.button.printpreview.alttext.text">
							<bean:message key="main.general.button.printpreview.text" />
						</html:submit></td>
				</html:form>
			</tr>
		</table>
		<br>
	</c:if>

	<table class="matrix" width="100%">
		<tr class="matrix">
			<th class="matrix" colspan="2"><span style="font-size: 12pt;"><bean:message
						key="main.matrixoverview.headline.hbtgmbh.text" /></span><br> <bean:message
					key="main.matrixoverview.headline.adress.text" />,<br> <bean:message
					key="main.matrixoverview.headline.place.text" />, <bean:message
					key="main.matrixoverview.headline.phone.text" /></th>
			<th class="matrix" colspan="${daysofmonth+1}">
				<center>
					<table width="60%">
						<tr>
							<th class="matrix noBborderStyle" colspan="3"><span
								style="font-size: 14pt; font-weight: bold;"><br> <bean:message
										key="main.matrixoverview.headline.tb.text" /><br> </span></th>
						</tr>
						<tr>
							<th width="33%" class="matrix noBborderStyle"><c:if
									test="${currentEmployee eq 'ALL EMPLOYEES'}">
									<bean:message
										key="main.matrixoverview.headline.allemployees.text" />
								</c:if> <c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">
									<c:out value="${currentEmployee}" />
								</c:if></th>
							<th width="33%" class="matrix noBborderStyle">
								<!--<bean:message
					key="main.matrixoverview.headline.month.text" />:--> <bean:message
									key="${MonthKey}" />
							</th>
							<th width="33%" class="matrix noBborderStyle">
								<!--<bean:message
					key="main.matrixoverview.headline.year.text" />:--> <c:out
									value="${currentYear}" />
							</th>
						</tr>
					</table>
				</center> <br>
			</th>
		</tr>

		<tr>
			<td class="matrix bold"><bean:message key="main.matrixoverview.table.order" /></td>
			<td class="matrix bold"><bean:message key="main.matrixoverview.table.orderdescription" /></td>

			<!-- <td>AuftragsBezeichnung</td> -->

			<c:forEach var="dayhourcount" items="${dayhourcounts}">

				<!-- 			<td align="center" class="matrix bold"> -->
				<c:if test="${dayhourcount.satSun==true}">
					<c:if test="${dayhourcount.publicHoliday==true}">
						<td
							title="${dayhourcount.publicHolidayName} / <bean:message key="${dayhourcount.weekDay}" />"
							class="matrix bold" align="right"
							style="background-color: c1c1c1;" id="matrixTableLink">
					</c:if>
					<c:if test="${dayhourcount.publicHoliday==false}">
						<td title="<bean:message key="${dayhourcount.weekDay}" />"
							class="matrix bold" align="right"
							style="background-color: lightgrey;" id="matrixTableLink">
					</c:if>
				</c:if>
				<c:if test="${dayhourcount.satSun==false}">
					<c:if test="${dayhourcount.publicHoliday==true}">
						<td
							title="${dayhourcount.publicHolidayName} / <bean:message key="${dayhourcount.weekDay}" />"
							class="matrix bold" align="right"
							style="background-color: c1c1c1;" id="matrixTableLink">
					</c:if>
					<c:if test="${dayhourcount.publicHoliday==false}">
						<td
							title="<c:if test="${dayhourcount.weekDay!=null}"><bean:message key="${dayhourcount.weekDay}" /></c:if>"
							class="matrix bold" align="right" id="matrixTableLink">
					</c:if>

				</c:if>
				<html:link
					href="/do/ShowDailyReport?day=${dayhourcount.dayString}&month=${currentMonth}&year=${currentYear}">
									&nbsp;<c:out value="${dayhourcount.dayString}" />&nbsp; 
				</html:link>
				<%-- ?task=refreshTimereports&day=${dayhourcount.dayString}&month=${currentMonth}&year=${currentYear} --%>
				</td>
			</c:forEach>
			<td class="matrix bold"><bean:message key="main.matrixoverview.table.sum.text" /></td>
		</tr>

		<c:forEach var="mergedreport" items="${mergedreports}">
			<tr class="matrix">
				<td class="matrix"><c:out value="${mergedreport.customOrder.sign}"></c:out><br><c:out value="${mergedreport.subOrder.sign}" /></td>
				<td class="matrix"><c:out value="${mergedreport.customOrder.shortdescription}"></c:out><br><c:out value="${mergedreport.subOrder.shortdescription}" /></td>
				<c:forEach var="bookingday" items="${mergedreport.bookingDays}">
					<c:if test="${bookingday.satSun==true}">
						<c:if test="${bookingday.publicHoliday==true}">
							<td title="${fn:escapeXml(bookingday.taskdescription)}"
								class="matrix" align="right"
								style="font-size: 7pt; border: 1px black solid; background-color: c1c1c1;">
						</c:if>
						<c:if test="${bookingday.publicHoliday==false}">
							<td title="${fn:escapeXml(bookingday.taskdescription)}"
								class="matrix" align="right"
								style="font-size: 7pt; border: 1px black solid; background-color: lightgrey;">
						</c:if>
					</c:if>
					<c:if test="${bookingday.satSun==false}">
						<c:if test="${bookingday.publicHoliday==true}">
							<td title="${fn:escapeXml(bookingday.taskdescription)}"
								class="matrix" align="right"
								style="font-size: 7pt; border: 1px black solid; background-color: c1c1c1;">
						</c:if>
						<c:if test="${bookingday.publicHoliday==false}">
							<td title="${fn:escapeXml(bookingday.taskdescription)}"
								class="matrix" align="right"
								style="font-size: 7pt; border: 1px black solid;">
						</c:if>
					</c:if>

					<c:choose>
						<c:when test="${(mergedreport.subOrder.sign eq overtimeCompensation and (not empty bookingday.taskdescription)
						and bookingday.durationHours eq '0' and bookingday.durationMinutes eq '0')}">
							<c:out value="${bookingday.durationString}" />
						</c:when>
						<c:otherwise>
							<c:if test="${(bookingday.durationHours eq '0' and bookingday.durationMinutes eq '0')}">
								&nbsp;
							</c:if>
							<c:if test="${!(bookingday.durationHours eq '0' and bookingday.durationMinutes eq '0')}">
								<c:out value="${bookingday.durationString}" />
							</c:if>
						</c:otherwise>
					</c:choose>
					</td>
				</c:forEach>
				<td class="matrix" align="right"><c:out	value="${mergedreport.sumString}"></c:out></td>
			</tr>
		</c:forEach>

		<tr class="matrix">
			<td colspan="2" class="matrix bold"	style="border-top: 2px black solid;" align="right"><bean:message key="main.matrixoverview.table.overall.text" /></td>
			<c:forEach var="dayhourcount" items="${dayhourcounts}">
				<c:if test="${dayhourcount.satSun==true}">
					<c:if test="${dayhourcount.publicHoliday==true}">
						<td class="matrix"
							style="font-size: 7pt; border-top: 2px black solid; background-color: c1c1c1;"
							align="right"><c:if
								test="${!(dayhourcount.workingHour eq '0.0')}">
								<c:out value="${dayhourcount.workingHourString}"></c:out>
							</c:if>
							<c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
					</c:if>
					<c:if test="${dayhourcount.publicHoliday==false}">
						<td class="matrix"
							style="font-size: 7pt; border-top: 2px black solid; background-color: lightgrey;"
							align="right"><c:if
								test="${!(dayhourcount.workingHour eq '0.0')}">
								<c:out value="${dayhourcount.workingHourString}"></c:out>
							</c:if>
							<c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
					</c:if>
				</c:if>
				<c:if test="${dayhourcount.satSun==false}">
					<c:if test="${dayhourcount.publicHoliday==true}">
						<td class="matrix"
							style="font-size: 7pt; border-top: 2px black solid; background-color: c1c1c1;"
							align="right"><c:if
								test="${!(dayhourcount.workingHour eq '0.0')}">
								<c:out value="${dayhourcount.workingHourString}"></c:out>
							</c:if>
							<c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
					</c:if>
					<c:if test="${dayhourcount.publicHoliday==false}">
						<td class="matrix"
							style="font-size: 7pt; border-top: 2px black solid;"
							align="right"><c:if
								test="${!(dayhourcount.workingHour eq '0.0')}">
								<c:out value="${dayhourcount.workingHourString}"></c:out>
							</c:if>
							<c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
					</c:if>
				</c:if>

			</c:forEach>
			<td class="matrix bold" style="border-top: 2px black solid;" align="right"><c:out value="${dayhourssumstring}"></c:out></td>
		</tr>

		<tr class="matrix">
			<td class="matrix" colspan="${daysofmonth+3}">
				<table>
					<tr class="matrix">
						<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.actualtime.text" /></td>
						<td class="matrix" style="border-style: none; text-align: right"><c:out	value="${dayhourssumstring}"></c:out></td>
					</tr>
					<tr class="matrix">
						<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.targettime.text" /></td>
						<td class="matrix" style="border-style: none; text-align: right"><c:out	value="${dayhourstargetstring}" /></td>
					</tr>
					<tr class="matrix">
						<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.difference.text" /></td>
						<td class="matrix" style="border-style:none;text-align: right;<c:if test="${dayhoursdiff.negative}">color:#FF0000;</c:if>"><c:out value="${dayhoursdiffstring}" /></td>
					</tr>
				</table>
			</td>
		</tr>

	</table>
	<table>
		<tr>
			<c:if
				test="${(loginEmployee.name == currentEmployee) || loginEmployee.id == currentEmployeeId || loginEmployee.status eq 'bl' || loginEmployee.status eq 'pv'|| loginEmployee.status eq 'adm'}">
				<html:form action="/CreateDailyReport?task=matrix">
					<td class="noBborderStyle" align="left"><html:submit
							styleId="button"
							titleKey="main.general.button.createnewreport.alttext.text">
							<bean:message key="main.general.button.createnewreport.text" />
						</html:submit></td>
				</html:form>
			</c:if>
			<html:form target="_blank" action="/ShowMatrix?task=print">
				<td class="noBborderStyle" align="left"><html:submit
						styleId="button"
						titleKey="main.general.button.printpreview.alttext.text">
						<bean:message key="main.general.button.printpreview.text" />
					</html:submit></td>
				<td
					style="border: 1px black solid; border-style: none none none none; text-align: right; color: red"
					class="bold matrix" colspan="2"><c:if test="${invalid}">
						<bean:message key="main.matrixoverview.table.invalid" />.
		</c:if></td>
			</html:form>
		</tr>
	</table>
</body>
</html>
