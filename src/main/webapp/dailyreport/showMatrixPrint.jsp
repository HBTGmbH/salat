<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<html>
<head>
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.matrix.title.text" /></title>
<jsp:include flush="true" page="/head-includes.jsp" />
<link rel="stylesheet" type="text/css" href="<c:url value="/style/matrixprint.css" />" media="all" />
<link rel="stylesheet" type="text/css" href="<c:url value="/style/print.css" />" media="print" />
<style>
	th {
		background-color: white;
		color: black;
	}
</style>
</head>
<body>
<form onsubmit="javascript:window.print();return false;">
<div align="right"><input class="hiddencontent" type="submit"
	value="Drucken"></div>
</form>
<table style="border:1px black solid;" class="matrix" width="100%">
	<tr class="matrix">
		<th class="matrix" colspan="2">
			<img src="<c:url value="/images/hbt-logo.svg" />" height="40px" style="padding: 5px" />
		</th>
		<th class="matrix" colspan="${daysofmonth+1}">
			<bean:message key="main.matrixoverview.headline.tb.text" /><br />
			<c:if test="${currentEmployee eq 'ALL EMPLOYEES'}">
				<bean:message key="main.matrixoverview.headline.allemployees.text" />
			</c:if>
			<c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">
				<c:out value="${currentEmployee}" />
			</c:if>
			<br />
			<bean:message key="${MonthKey}" /> <c:out value="${currentYear}" />
		</th>
	</tr>

	<tr>
		<td class="matrix bold"><bean:message
			key="main.matrixoverview.table.order" /></td>
		<td class="matrix bold"><bean:message
			key="main.matrixoverview.table.description" /></td>
		<!-- <td>AuftragsBezeichnung</td> -->
		<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">

			<!-- 			<td align="center" class="matrix bold"> -->
			<c:if test="${matrixdaytotal.satSun==true}">
				<c:if test="${matrixdaytotal.publicHoliday==true}">
					<td title="${matrixdaytotal.publicHolidayName} / <bean:message key="${matrixdaytotal.weekDay}" />" class="matrix bold" align="right" style="background-color:c1c1c1;">
				</c:if>
				<c:if test="${matrixdaytotal.publicHoliday==false}">
					<td title="<bean:message key="${matrixdaytotal.weekDay}" />" class="matrix bold" align="right" style="background-color:lightgrey;">
				</c:if>
			</c:if>
			<c:if test="${matrixdaytotal.satSun==false}">
				<c:if test="${matrixdaytotal.publicHoliday==true}">
					<td style="color:#c1c1c1;"
						title="${matrixdaytotal.publicHolidayName} / <bean:message
					key="${matrixdaytotal.weekDay}" />"
						class="matrix bold" align="right" style="background-color:c1c1c1;">
				</c:if>
				<c:if test="${matrixdaytotal.publicHoliday==false}">
					<td title="<bean:message
					key="${matrixdaytotal.weekDay}" />"
						class="matrix bold" align="right">
				</c:if>

			</c:if>
			&nbsp;<c:out value="${matrixdaytotal.dayString}" />&nbsp;
		
		</td>
		</c:forEach>
		<td class="matrix bold"><bean:message
			key="main.matrixoverview.table.sum.text" /></td>
	</tr>

	<c:forEach var="matrixline" items="${matrixlines}">
		<tr class="matrix">
			<td class="matrix" style="font-size: 6pt;border:1px black solid;">
			<c:out value="${matrixline.customOrder.sign}" /><br>
			<c:out value="${matrixline.subOrder.sign}" /></td>
			<td class="matrix" style="font-size: 6pt;border:1px black solid;">
			<c:out value="${matrixline.customOrder.shortdescription}"></c:out><br>
			<c:out value="${matrixline.subOrder.shortdescription}" /></td>
			<c:forEach var="bookingday" items="${matrixline.bookingDays}">
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
					<c:out value="${bookingday.durationString}"></c:out>
				</td>
			</c:forEach>
			<td class="matrix" align="right"><c:out
				value="${matrixline.totalString}"></c:out></td>
		</tr>
	</c:forEach>
	<tr class="matrix">
		<td colspan="2" class="matrix bold"
			style="border-top:2px black solid;" align="right"><bean:message
			key="main.matrixoverview.table.overall.text" /></td>
		<c:forEach var="matrixdaytotal" items="${matrixdaytotals}">

			<c:if test="${matrixdaytotal.satSun==true}">
				<c:if test="${matrixdaytotal.publicHoliday==true}">
					<td class="matrix" style="font-size: 6pt;border-top:2px black solid;background-color:c1c1c1;" align="right">
						<c:out value="${matrixdaytotal.workingTimeString}"></c:out>
					</td>
				</c:if>
				<c:if test="${matrixdaytotal.publicHoliday==false}">
					<td class="matrix" style="font-size: 6pt;border-top:2px black solid;background-color:lightgrey;" align="right">
						<c:out value="${matrixdaytotal.workingTimeString}"></c:out>
					</td>
				</c:if>
			</c:if>
			<c:if test="${matrixdaytotal.satSun==false}">
				<c:if test="${matrixdaytotal.publicHoliday==true}">
					<td class="matrix" style="font-size: 6pt;border-top:2px black solid;background-color:c1c1c1;" align="right">
						<c:out value="${matrixdaytotal.workingTimeString}"></c:out>
					</td>
				</c:if>
				<c:if test="${matrixdaytotal.publicHoliday==false}">
					<td class="matrix"
						style="font-size: 6pt;border-top:2px black solid;" align="right">
						<c:out value="${matrixdaytotal.workingTimeString}"></c:out>
					</td>
				</c:if>
			</c:if>

		</c:forEach>
		<td class="matrix bold" style="border-top:2px black solid;"
			align="right"><c:out value="${totalworkingtimestring}"></c:out></td>
	</tr>
</table>
<br>
<br>
</body>
</html>
