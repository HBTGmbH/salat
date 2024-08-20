<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html>
<head>
	<title>
		<bean:message key="main.general.application.title" /> -
		<bean:message key="main.general.mainmenu.matrix.title.text" />
	</title>
	<jsp:include flush="true" page="/head-includes.jsp" />
	<script type="text/javascript" language="JavaScript">

		function setSwitchEmployee(form) {
			form.action = "/do/ShowMatrix?task=switchEmployee";
			form.submit();
		}

		function setRefreshMatrixAction(form) {
			form.action = "/do/ShowMatrix?task=refreshMatrix";
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
	<link rel="stylesheet" href="<c:url value="/webjars/bootstrap-icons/font/bootstrap-icons.min.css"/>">
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
						onchange="setSwitchEmployee(this.form)"
						styleClass="make-select2">
						<c:if test="${authorizedUser.manager}">
							<html:option value="-1">
								<bean:message key="main.general.allemployees.text" />
							</html:option>
						</c:if>

						<c:forEach var="employeecontract" items="${employeecontracts}">
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

			<!-- select order -->
			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.monthlyreport.customerorder.text" />:</b>
				</td>
				<td align="left" class="noBborderStyle"><html:select
						property="order"
						value="<%=(String) request.getSession().getAttribute(\"currentOrder\")%>"
						onchange="setRefreshMatrixAction(this.form)"
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
							onchange="setRefreshMatrixAction(this.form)" styleClass="make-select2">
							<html:options collection="days" property="value"
								labelProperty="label" />
						</html:select>
					</c:if> <html:select property="fromMonth" value="${currentMonth}"
						onchange="setRefreshMatrixAction(this.form)" styleClass="make-select2">
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
						onchange="setRefreshMatrixAction(this.form)" styleClass="make-select2">
						<html:options collection="years" property="value"
							labelProperty="label" />
					</html:select>

					<c:if test="${matrixview != 'custom'}">
						<br />
						<%-- Arrows for navigating the month --%>
						<a href="javascript:setMonth('-12')"><i class="bi bi-skip-backward-btn"></i></a>
						<a href="javascript:setMonth('-1')"><i class="bi bi-skip-start-btn"></i></a>
						<a href="javascript:setMonth('0')"><i class="bi bi-stop-btn"></i></a>
						<a href="javascript:setMonth('1')"><i class="bi bi-skip-end-btn"></i></a>
						<a href="javascript:setMonth('12')"><i class="bi bi-skip-forward-btn"></i></a>
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
							onchange="setRefreshMatrixAction(this.form)" styleClass="make-select2">
							<html:options collection="days" property="value"
								labelProperty="label" />
						</html:select> <html:select property="untilMonth" value="${lastMonth}"
							onchange="setRefreshMatrixAction(this.form)" styleClass="make-select2">
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
							onchange="setRefreshMatrixAction(this.form)" styleClass="make-select2">
							<html:options collection="years" property="value"
								labelProperty="label" />
						</html:select></td>
				</tr>
			</c:if>
			<!-- select invoice -->
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message key="main.monthlyreport.invoice.text" />:</b></td>
				<td align="left" class="noBborderStyle"><html:checkbox property="invoice" onclick="setRefreshMatrixAction(this.form)" /></td>
			</tr>
			<!-- select invoice -->
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message key="main.monthlyreport.non.invoice.text" />:</b></td>
				<td align="left" class="noBborderStyle"><html:checkbox property="nonInvoice" onclick="setRefreshMatrixAction(this.form)" /></td>
			</tr>
			<c:if test="${dailyReportViewHelper.displayWorkingDayStartBreak}">
				<!-- select start and break times -->
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message key="main.monthlyreport.startandbreaktime.text" />:</b></td>
					<td align="left" class="noBborderStyle"><html:checkbox property="startAndBreakTime" onclick="setRefreshMatrixAction(this.form)" /></td>
				</tr>
			</c:if>
		</table>
	</html:form>

	<bean:size id="matrixlinesSize" name="matrixlines" />
	<c:if test="${matrixlinesSize>10}">
		<table>
			<tr>
				<c:if
					test="${(loginEmployee.name == currentEmployee) || loginEmployee.id == currentEmployeeId || authorizedUser.manager}">
					<html:form action="/CreateDailyReport?task=matrix">
						<td class="noBborderStyle" align="left"><html:submit
								styleId="button"
								titleKey="main.general.button.createnewreport.alttext.text">
								<bean:message key="main.general.button.createnewreport.text" />
							</html:submit></td>
					</html:form>
				</c:if>
				<html:form target="_blank"
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
			<th class="matrix" colspan="2"></th>
			<th class="matrix" colspan="${daysofmonth+1}" align="left">
				<c:if test="${currentEmployee eq 'ALL EMPLOYEES'}">
					<bean:message
						key="main.matrixoverview.headline.allemployees.text" />
				</c:if>
				<c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">
					<c:out value="${currentEmployee}" />
				</c:if>
				-
				<bean:message key="${MonthKey}" />
				<c:out value="${currentYear}" />
			</th>
		</tr>

		<tr>
			<td class="matrix bold"><bean:message key="main.matrixoverview.table.order" /></td>
			<td class="matrix bold"><bean:message key="main.matrixoverview.table.orderdescription" /></td>
			<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
				<td title="${matrixdaytotal.publicHolidayName} / <bean:message key="${matrixdaytotal.weekDay}" />"
					class="matrix bold${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
					align="center"
					id="matrixTableLink">
					<html:link href="/do/ShowDailyReport?day=${matrixdaytotal.dayString}&month=${currentMonth}&year=${currentYear}">
						&nbsp;<c:out value="${matrixdaytotal.dayString}" />&nbsp;
					</html:link>
				</td>
			</c:forEach>
			<td class="matrix bold" align="right"><bean:message key="main.matrixoverview.table.sum.text" /></td>
		</tr>

		<c:forEach var="matrixline" items="${matrixlines}">
			<tr class="matrix">
				<td class="matrix"><c:out value="${matrixline.customOrder.sign}"></c:out><br><c:out value="${matrixline.subOrder.sign}" /></td>
				<td class="matrix"><c:out value="${matrixline.customOrder.shortdescription}"></c:out><br><c:out value="${matrixline.subOrder.shortdescription}" /></td>
				<c:forEach var="bookingday" items="${matrixline.bookingDays}">
					<td title="${fn:escapeXml(bookingday.taskdescription)}"
					class="matrix${bookingday.publicHoliday ? ' holiday' : (bookingday.satSun ? ' weekend' : '')}"
					align="right"
					style="font-size: 7pt; border: 1px black solid;">
						<c:out value="${bookingday.bookingCount gt 0 ? bookingday.durationString : ' '}" />
					</td>
				</c:forEach>
				<td class="matrix" align="right"><c:out	value="${matrixline.totalString}"></c:out></td>
			</tr>
		</c:forEach>

		<tr class="matrix">
			<td colspan="2" class="matrix bold"	style="border-top: 2px black solid;" align="right"><bean:message key="main.matrixoverview.table.overall.text" /></td>
			<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
				<td class="matrix bold${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
					style="font-size: 7pt; border-top: 2px black solid;"
					align="right">
					<c:out value="${matrixdaytotal.workingTimeString}"></c:out>
				</td>
			</c:forEach>
			<td class="matrix bold" style="border-top: 2px black solid;" align="right"><c:out value="${totalworkingtimestring}"></c:out></td>
		</tr>

		<c:if test="${dailyReportViewHelper.displayOvertimeCompensation and not totalovertimecompensation.zero}">
		<tr class="matrix">
			<td colspan="2" class="matrix bold"	align="right"><bean:message key="main.matrixoverview.table.overtimecompensation.text" /></td>
			<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
				<td class="matrix bold${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
					style="font-size: 7pt;"
					align="right">
					<java8:formatDuration value="${matrixdaytotal.effectiveOvertime}" />
				</td>
			</c:forEach>
			<td class="matrix bold" align="right"><c:out value="${totalovertimecompensationstring}"></c:out></td>
		</tr>
		</c:if>

		<c:if test="${dailyReportViewHelper.displayWorkingDayStartBreak and showStartAndBreakTime}">
			<tr class="matrix">
				<td colspan="2" class="matrix"	style="border-top: 2px black solid;" align="right"><bean:message key="main.matrixoverview.table.startofwork.text" /></td>
				<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
							<td class="matrix${matrixdaytotal.invalidStartOfWork ? ' invalid' : (matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : ''))}"
								style="font-size: 7pt; border-top: 2px black solid;"
								align="right">
								<c:out value="${matrixdaytotal.zeroWorkingTime ? ' ' : matrixdaytotal.startOfWorkString}"></c:out>
							</td>
				</c:forEach>
				<td class="matrix" style="font-size: 7pt; border-top: 2px black solid;" align="right">&nbsp;</td>
			</tr>

			<tr class="matrix">
				<td colspan="2" class="matrix"	style="border-top: 1px black solid;" align="right"><bean:message key="main.matrixoverview.table.breakduration.text" /></td>
				<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
							<td class="matrix${matrixdaytotal.invalidBreakTime ? ' invalid' : (matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : ''))}"
								style="font-size: 7pt; border-top: 1x black solid;"
								align="right">
								<c:out value="${matrixdaytotal.zeroWorkingTime ? ' ' : matrixdaytotal.breakDurationString}"></c:out>
							</td>
				</c:forEach>
				<td class="matrix" align="right">&nbsp;</td>
			</tr>
		</c:if>
		<c:if test="${dailyReportViewHelper.displayWorkingDay}">
			<tr class="matrix">
				<td colspan="2" class="matrix"	style="border-top: 1px black solid;" align="right"><bean:message key="main.matrixoverview.table.notworked.text" /></td>
				<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
					<td class="matrix${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
						style="font-size: 7pt; border-top: 1x black solid;"
						align="center">
						<c:out value="${matrixdaytotal.notWorked ? 'x' : matrixdaytotal.partiallyNotWorked ? '(x)' : ' '}"></c:out>
					</td>
				</c:forEach>
				<td class="matrix" align="right">&nbsp;</td>
			</tr>
		</c:if>

		<c:if test="${dailyReportViewHelper.displayTargetHours}">
			<tr class="matrix">
				<td class="matrix" colspan="${daysofmonth+3}">
					<table>
						<tr class="matrix">
							<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.actualtime.text" /></td>
							<td class="matrix" style="border-style: none; text-align: right"><c:out	value="${totalworkingtimestring}"></c:out></td>
						</tr>
						<c:if test="${totalworkingtimetarget != null}">
							<tr class="matrix">
								<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.targettime.text" /></td>
								<td class="matrix" style="border-style: none; text-align: right"><c:out	value="${totalworkingtimetargetstring}" /></td>
							</tr>
							<c:if test="${not totalovertimecompensation.zero}">
								<tr class="matrix">
									<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.overtimecompensation.text" /></td>
									<td class="matrix" style="border-style:none;text-align: right"><c:out value="${totalovertimecompensationstring}" /></td>
								</tr>
							</c:if>
							<tr class="matrix">
								<td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.difference.text" /></td>
								<td class="matrix" style="border-style:none;text-align: right;<c:if test="${totalworkingtimediff.negative}">color:#FF0000;</c:if>"><c:out value="${totalworkingtimediffstring}" /></td>
							</tr>
						</c:if>
					</table>
				</td>
			</tr>
		</c:if>

	</table>
	<table>
		<tr>
			<c:if
				test="${loginEmployee.name == currentEmployee || loginEmployee.id == currentEmployeeId || authorizedUser.manager}">
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
	<c:if test="${dailyReportViewHelper.displayEmployeeInfo}">
		<!-- Überstunden und Urlaubstage -->
		<br><br><br>
		<jsp:include flush="true" page="/info2.jsp">
			<jsp:param name="info" value="Info" />
		</jsp:include>
	</c:if>
</body>
</html>
