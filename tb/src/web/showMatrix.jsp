<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>

<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.matrix.title.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" media="all" />
<link rel="stylesheet" type="text/css" href="/tb/print.css"
	media="print" />
<script type="text/javascript" language="JavaScript">	
 	function setUpdateMergedreportsAction(form) {	
 		form.action = "/tb/do/ShowMatrix?task=refreshMergedreports";
		form.submit();
	}
</script>
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<p>
<h2><bean:message key="main.general.mainmenu.matrix.text" /></h2>
</p>
<br>
<html:form action="/ShowMatrix">
	<table class="center backgroundcolor">
		
		<!-- select employee -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.employee.fullname.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="employeeId" value="${currentEmployeeId}"
				onchange="setUpdateMergedreportsAction(this.form)">

				<!--<html:option value="-1">
					<bean:message key="main.general.allemployees.text" />
				</html:option>-->

				<html:options collection="employeeswithcontract"
					labelProperty="name" property="id" />
			</html:select> <!--  
			<logic:equal name="currentEmployeeId" value="-1"
				scope="session">
				<span style="color:red"> <b><bean:message
					key="main.general.selectemployee.editable.text" />.</b> </span>
			</logic:equal>
			--></td>
		</tr>

		<!-- select order -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.customerorder.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="order"
				value="<%=(String) request.getSession().getAttribute("currentOrder")%>"
				onchange="setUpdateMergedreportsAction(this.form)">

				<html:option value="ALL ORDERS">
					<bean:message key="main.general.allorders.text" />
				</html:option>

				<html:options collection="orders" labelProperty="sign"
					property="sign" />
				<html:hidden property="orderId" />
			</html:select></td>
		</tr>

		<!-- select view mode
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.general.timereport.view.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="matrixview"
				onchange="setUpdateMergedreportsAction(this.form)">
				<html:option value="month">
					<bean:message key="main.general.timereport.view.monthly.text" />
				</html:option>
				<html:option value="custom">
					<bean:message key="main.general.timereport.view.custom.text" />
				</html:option>

			</html:select></td>
		</tr>
		-->
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
					onchange="setUpdateMergedreportsAction(this.form)">
					<html:options collection="days" property="value"
						labelProperty="label" />
				</html:select>
			</c:if> <html:select property="fromMonth" value="${currentMonth}"
				onchange="setUpdateMergedreportsAction(this.form)">
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
				onchange="setUpdateMergedreportsAction(this.form)">
				<html:options collection="years" property="value"
					labelProperty="label" />
			</html:select></td>
		</tr>

		<!-- select second date -->
		<c:if test="${matrixview eq 'custom'}">
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
					key="main.monthlyreport.daymonthyear.text" />:</b></td>
				<td align="left" class="noBborderStyle"><html:select
					property="untilDay" value="${lastDay}"
					onchange="setUpdateMergedreportsAction(this.form)">
					<html:options collection="days" property="value"
						labelProperty="label" />
				</html:select> <html:select property="untilMonth" value="${lastMonth}"
					onchange="setUpdateMergedreportsAction(this.form)">
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
					onchange="setUpdateMergedreportsAction(this.form)">
					<html:options collection="years" property="value"
						labelProperty="label" />
				</html:select></td>
			</tr>
		</c:if>
	</table>
</html:form>

<table class="matrix" width="100%">
	<tr class="matrix">
		<th class="matrix" colspan="2"><span style="font-size:12pt;"><bean:message
			key="main.matrixoverview.headline.hbtgmbh.text" /></span><br>
		<bean:message key="main.matrixoverview.headline.adress.text" />,<br>
		<bean:message key="main.matrixoverview.headline.place.text" />, <bean:message
			key="main.matrixoverview.headline.phone.text" /></th>
		<th class="matrix" colspan="${daysofmonth+1}">
		<center>
		<table width="60%">
			<tr>
				<th class="matrix noBborderStyle" colspan="3">
				<h2><bean:message key="main.matrixoverview.headline.tb.text" /></h2>
				</th>
			</tr>
			<tr>
				<th width="33%" class="matrix noBborderStyle"><!--<bean:message
					key="main.matrixoverview.headline.name.text" />:--> <c:out
					value="${currentEmployee}" /></th>
				<th width="33%" class="matrix noBborderStyle"><!--<bean:message
					key="main.matrixoverview.headline.month.text" />:--> <c:out
					value="${currentMonth}" /></th>
				<th width="33%" class="matrix noBborderStyle"><!--<bean:message
					key="main.matrixoverview.headline.year.text" />:--> <c:out
					value="${currentYear}" /></th>
			</tr>
		</table>
		</center>
		<br>
		</th>
	</tr>

	<!-- <tr>
		<td colspan="2" class="matrix bold">Kalenderwoche / Stunden</td>
		<td colspan="${daysofmonth}" class="matrix bold">n/a</td>
		<td rowspan="2" class="matrix bold">Summe</td>
	</tr> -->

	<tr>
		<td class="matrix bold">
			<!--
			<bean:message
				key="main.matrixoverview.table.customerordernr.text" />
			-->
			<bean:message
				key="main.matrixoverview.table.order" />
		</td>
		<td class="matrix bold">
			<!-- 
			<bean:message
				key="main.matrixoverview.table.subordernr.text" />
			-->
			<bean:message
				key="main.matrixoverview.table.description" />
		</td>
		
		
		<!-- <td>AuftragsBezeichnung</td> -->
		
		<c:forEach var="dayhourcount" items="${dayhourcounts}">

			<!-- 			<td align="center" class="matrix bold"> -->
			<c:if test="${dayhourcount.satSun==true}">
				<c:if test="${dayhourcount.publicHoliday==true}">
					<td
						title="${dayhourcount.publicHolidayName} / <bean:message
					key="${dayhourcount.weekDay}" />"
						class="matrix bold" align="right" style="background-color:c1c1c1;">
				</c:if>
				<c:if test="${dayhourcount.publicHoliday==false}">
					<td title="<bean:message
					key="${dayhourcount.weekDay}" />"
						class="matrix bold" align="right"
						style="background-color:lightgrey;">
				</c:if>
			</c:if>
			<c:if test="${dayhourcount.satSun==false}">
				<c:if test="${dayhourcount.publicHoliday==true}">
					<td
						title="${dayhourcount.publicHolidayName} / <bean:message
					key="${dayhourcount.weekDay}" />"
						class="matrix bold" align="right" style="background-color:c1c1c1;">
				</c:if>
				<c:if test="${dayhourcount.publicHoliday==false}">
					<td title="<bean:message
					key="${dayhourcount.weekDay}" />"
						class="matrix bold" align="right">
				</c:if>

			</c:if>
			&nbsp;<c:out value="${dayhourcount.dayString}" />&nbsp;
		
		</td>
		</c:forEach>
		<td class="matrix bold"><bean:message
			key="main.matrixoverview.table.sum.text" /></td>
	</tr>

	<c:forEach var="mergedreport" items="${mergedreports}">
		<tr class="matrix">
			<td class="matrix">
			<c:out value="${mergedreport.customOrder.sign}"></c:out><br><c:out value="${mergedreport.subOrder.sign}" /></td>
			<td class="matrix"><c:out value="${mergedreport.customOrder.shortdescription}"></c:out><br>
			<c:out value="${mergedreport.subOrder.shortdescription}" /></td></td>
			<c:forEach var="bookingday" items="${mergedreport.bookingDay}">
				<c:if test="${bookingday.satSun==true}">
					<c:if test="${bookingday.publicHoliday==true}">
						<td title="${bookingday.taskdescription}" class="matrix"
							align="right"
							style="font-size: 7pt;border:1px black solid;background-color:c1c1c1;">
					</c:if>
					<c:if test="${bookingday.publicHoliday==false}">
						<td title="${bookingday.taskdescription}" class="matrix"
							align="right"
							style="font-size: 7pt;border:1px black solid;background-color:lightgrey;">
					</c:if>
				</c:if>
				<c:if test="${bookingday.satSun==false}">
					<c:if test="${bookingday.publicHoliday==true}">
						<td title="${bookingday.taskdescription}" class="matrix"
							align="right"
							style="font-size: 7pt;border:1px black solid;background-color:c1c1c1;">
					</c:if>
					<c:if test="${bookingday.publicHoliday==false}">
						<td title="${bookingday.taskdescription}" class="matrix"
							align="right" style="font-size: 7pt;border:1px black solid;">
					</c:if>

				</c:if>
				<c:if
					test="${(bookingday.durationHours eq '0' and bookingday.durationMinutes eq '0')}">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</c:if>
				<c:if
					test="${!(bookingday.durationHours eq '0' and bookingday.durationMinutes eq '0')}">
					<!--<c:out
						value="${(((bookingday.durationHours*60)+(bookingday.durationMinutes))/60)}"></c:out>-->
					<c:out value="${bookingday.roundHours}"></c:out>
				</c:if>
				</td>
			</c:forEach>
			<td class="matrix" align="right"><c:out
				value="${mergedreport.roundSum}"></c:out></td>
		</tr>
	</c:forEach>
	<tr class="matrix">
		<td colspan="2" class="matrix bold"
			style="border-top:2px black solid;" align="right"><bean:message
			key="main.matrixoverview.table.overall.text" /></td>
		<c:forEach var="dayhourcount" items="${dayhourcounts}">

			<td class="matrix" style="font-size: 7pt;border-top:2px black solid;"
				align="right"><c:if
				test="${!(dayhourcount.workingHour eq '0.0')}">
				<c:out value="${dayhourcount.roundWorkingHour}"></c:out>
			</c:if><c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>

		</c:forEach>
		<td class="matrix bold" style="border-top:2px black solid;"
			align="right"><c:out value="${dayhourssum}"></c:out></td>
	</tr>

	<tr class="matrix">
		<td class="matrix" colspan="34">
		<table>
			<tr class="matrix">
				<td class="matrix" style="border-style:none;"><bean:message
					key="main.matrixoverview.headline.actualtime.text" /></td>
				<td class="matrix underline" style="border-style:none;"><c:out
					value="${dayhourssum}"></c:out></td>
			</tr>
			<tr class="matrix">
				<td class="matrix" style="border-style:none;"><bean:message
					key="main.matrixoverview.headline.targettime.text" /></td>
				<td class="matrix underline" style="border-style:none;"><c:if
					test="${!(dayhourssum eq '0.0')}">
					<c:out value="${dayhourstarget}"></c:out>
				</c:if><c:if test="${(dayhourssum eq '0.0')}">0.0</c:if></td>
			</tr>
			<tr class="matrix">
				<td class="matrix" style="border-style:none;"><bean:message
					key="main.matrixoverview.headline.difference.text" /></td>
				<td class="matrix underline" style="border-style:none;"><c:if
					test="${!(dayhourssum eq '0.0')}">
					<c:out value="${dayhoursdiff}"></c:out>
				</c:if><c:if test="${(dayhourssum eq '0.0')}">0.0</c:if></td>
			</tr>
		</table>
		</td>
	</tr>
</table>
<table>
	<tr>
		<c:if
			test="${(loginEmployee.name == currentEmployee) || loginEmployee.id == currentEmployeeId || loginEmployee.status eq 'bl' || loginEmployee.status eq 'gf'|| loginEmployee.status eq 'adm'}">
			<html:form action="/CreateDailyReport?task=matrix">
				<td class="noBborderStyle" align="left"><html:submit
					styleId="button">
					<bean:message key="main.general.button.createnewreport.text" />
				</html:submit></td>
			</html:form>
		</c:if>
	</tr>
	<br>
	<tr>
		<html:form target="fenster"
			onsubmit="window.open('','fenster','width=800,height=400,resizable=yes')"
			action="/ShowMatrix?task=print">
			<td class="noBborderStyle" align="left"><html:submit
				styleId="button">
				<bean:message key="main.general.button.printpreview.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
</body>
</html>
