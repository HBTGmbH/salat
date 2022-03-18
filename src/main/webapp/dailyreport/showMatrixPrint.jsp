<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<html>
<head>

<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.matrix.title.text" /></title>
<link rel="stylesheet" type="text/css" href="/style/matrixprint.css"
	media="all" />
<link rel="stylesheet" type="text/css" href="/style/print.css"
	media="print" />
<script type="text/javascript" language="JavaScript">	
 	function setUpdateMergedreportsAction(form) {	
 		form.action = "/do/ShowMatrix?task=refreshMergedreports";
		form.submit();
	}
</script>
</head>
<body>
<FORM ONSUBMIT="javascript:window.print();return false;">
<div align="right"><input class="hiddencontent" type="submit"
	value="Drucken"></div>
</form>
<table style="border:1px black solid;" class="matrix" width="100%">
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
				<th class="matrix noBborderStyle" colspan="2"><span
					style="font-size:12pt;"><bean:message
					key="main.matrixoverview.headline.tb.text" /></span></th>
			</tr>
			<tr>				
				<th width="50%" class="matrix noBborderStyle" align="right"><!--<bean:message
					key="main.matrixoverview.headline.month.text" />:--> <bean:message
					key="${MonthKey}" /></th>
				<th width="50%" class="matrix noBborderStyle" align="left"><!--<bean:message
					key="main.matrixoverview.headline.year.text" />:--> <c:out
					value="${currentYear}" /></th>
			</tr>
			<tr>
				<th width="100%" class="matrix noBborderStyle" colspan="2" align="center"><c:if
					test="${currentEmployee eq 'ALL EMPLOYEES'}">
					<bean:message key="main.matrixoverview.headline.allemployees.text" />
				</c:if> <c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">
					<c:out value="${currentEmployee}" />
				</c:if></th>
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
		<td class="matrix bold"><bean:message
			key="main.matrixoverview.table.order" /></td>
		<td class="matrix bold"><bean:message
			key="main.matrixoverview.table.description" /></td>
		<!-- <td>AuftragsBezeichnung</td> -->
		<c:forEach var="dayhourcount" items="${dayhourcounts}">

			<!-- 			<td align="center" class="matrix bold"> -->
			<c:if test="${dayhourcount.satSun==true}">
				<c:if test="${dayhourcount.publicHoliday==true}">
					<td title="${dayhourcount.publicHolidayName} / <bean:message key="${dayhourcount.weekDay}" />" class="matrix bold" align="right" style="background-color:c1c1c1;">
				</c:if>
				<c:if test="${dayhourcount.publicHoliday==false}">
					<td title="<bean:message key="${dayhourcount.weekDay}" />" class="matrix bold" align="right" style="background-color:lightgrey;">
				</c:if>
			</c:if>
			<c:if test="${dayhourcount.satSun==false}">
				<c:if test="${dayhourcount.publicHoliday==true}">
					<td style="color:#c1c1c1;"
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
			<td class="matrix" style="font-size: 6pt;border:1px black solid;">
			<c:out value="${mergedreport.customOrder.sign}" /><br>
			<c:out value="${mergedreport.subOrder.sign}" /></td>
			<td class="matrix" style="font-size: 6pt;border:1px black solid;">
			<c:out value="${mergedreport.customOrder.shortdescription}"></c:out><br>
			<c:out value="${mergedreport.subOrder.shortdescription}" /></td>
			<c:forEach var="bookingday" items="${mergedreport.bookingDays}">
				<c:if test="${bookingday.satSun==true}">
					<c:if test="${bookingday.publicHoliday==true}">
						<td title="${fn:escapeXml(bookingday.taskdescription)}" class="matrix" align="right" style="font-size: 6pt;border:1px black solid;background-color:c1c1c1;">
					</c:if>
					<c:if test="${bookingday.publicHoliday==false}">
						<td title="${fn:escapeXml(bookingday.taskdescription)}" class="matrix" align="right" style="font-size: 6pt;border:1px black solid;background-color:lightgrey;">
					</c:if>
				</c:if>
				<c:if test="${bookingday.satSun==false}">
					<c:if test="${bookingday.publicHoliday==true}">
						<td title="${fn:escapeXml(bookingday.taskdescription)}" class="matrix"
							align="right"
							style="font-size: 6pt;border:1px black solid;background-color:c1c1c1;">
					</c:if>
					<c:if test="${bookingday.publicHoliday==false}">
						<td title="${fn:escapeXml(bookingday.taskdescription)}" class="matrix"
							align="right" style="font-size: 6pt;border:1px black solid;">
					</c:if>

				</c:if>
				<c:if
					test="${bookingday.bookingCount eq 0}">&nbsp;&nbsp;&nbsp;&nbsp;</c:if>
				<c:if
					test="${bookingday.bookingCount gt 0}">
					<c:out value="${bookingday.durationString}"></c:out>
				</c:if>
				</td>
			</c:forEach>
			<td class="matrix" align="right"><c:out
				value="${mergedreport.sumString}"></c:out></td>
		</tr>
	</c:forEach>
	<tr class="matrix">
		<td colspan="2" class="matrix bold"
			style="border-top:2px black solid;" align="right"><bean:message
			key="main.matrixoverview.table.overall.text" /></td>
		<c:forEach var="dayhourcount" items="${dayhourcounts}">

			<c:if test="${dayhourcount.satSun==true}">
				<c:if test="${dayhourcount.publicHoliday==true}">
					<td class="matrix"
						style="font-size: 6pt;border-top:2px black solid;background-color:c1c1c1;"
						align="right"><c:if
						test="${!(dayhourcount.workingHour eq '0.0')}">
						<c:out value="${dayhourcount.workingHourString}"></c:out>
					</c:if><c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
				</c:if>
				<c:if test="${dayhourcount.publicHoliday==false}">
					<td class="matrix"
						style="font-size: 6pt;border-top:2px black solid;background-color:lightgrey;"
						align="right"><c:if
						test="${!(dayhourcount.workingHour eq '0.0')}">
						<c:out value="${dayhourcount.workingHourString}"></c:out>
					</c:if><c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
				</c:if>
			</c:if>
			<c:if test="${dayhourcount.satSun==false}">
				<c:if test="${dayhourcount.publicHoliday==true}">
					<td class="matrix"
						style="font-size: 6pt;border-top:2px black solid;background-color:c1c1c1;"
						align="right"><c:if
						test="${!(dayhourcount.workingHour eq '0.0')}">
						<c:out value="${dayhourcount.workingHourString}"></c:out>
					</c:if><c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
				</c:if>
				<c:if test="${dayhourcount.publicHoliday==false}">
					<td class="matrix"
						style="font-size: 6pt;border-top:2px black solid;" align="right"><c:if
						test="${!(dayhourcount.workingHour eq '0.0')}">
						<c:out value="${dayhourcount.workingHourString}"></c:out>
					</c:if><c:if test="${(dayhourcount.workingHour eq '0.0')}">&nbsp;</c:if></td>
				</c:if>
			</c:if>

		</c:forEach>
		<td class="matrix bold" style="border-top:2px black solid;"
			align="right"><c:out value="${dayhourssumstring}"></c:out></td>
	</tr>
</table>
<br>
<br>
<table>
	<tr>
		<td
			style="font-size: 12pt;border:1px black solid;border-style:none none solid none;"
			class="bold matrix" width="250px">&nbsp;</td>
		<td style="font-size: 12pt;border:0px black solid;" width="40px">&nbsp;</td>
		<td
			style="font-size: 12pt;border:1px black solid;border-style:none none solid none;"
			class="bold matrix" width="250px">&nbsp;</td>
		<td
			style="border:1px black solid;border-style:none none none none;text-align:right;"
			class="bold matrix" width="500px"><c:if test="${acceptance}">
			<bean:message key="main.matrixoverview.table.accepted" />
			<c:out value="${acceptedby}" />
		</c:if></td>
	</tr>
</table>
</body>
</html>
