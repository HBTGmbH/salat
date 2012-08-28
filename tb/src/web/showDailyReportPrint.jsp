<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">


<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.daily.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/matrixprint.css"
	media="all" />
<link rel="stylesheet" type="text/css" href="/tb/print.css"
	media="print" />

</head>
<body>
<FORM ONSUBMIT="javascript:window.print();return false;">
<div align="right"><input class="hiddencontent" type="submit"
	value="Drucken"></div>
</form>
<table style="border:1px black solid;" class="matrix" width="100%">
	<tr class="matrix">
		<th class="matrix" colspan="2" width="25%"><span
			style="font-size:12pt;"><bean:message
			key="main.matrixoverview.headline.hbtgmbh.text" /></span><br>
		<bean:message key="main.matrixoverview.headline.adress.text" />,<br>
		<bean:message key="main.matrixoverview.headline.place.text" />, <bean:message
			key="main.matrixoverview.headline.phone.text" /></th>
		<th class="matrix"
			colspan="<c:if test="${(currentEmployee eq 'ALL EMPLOYEES')}">3</c:if><c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">4</c:if>">
		<center>
		<table width="60%">
			<tr>
				<th class="matrix noBborderStyle" align="center"
					colspan="<c:if test="${(view eq 'day')}">3</c:if><c:if test="${(view eq 'month')}">2</c:if><c:if test="${(view eq 'custom')}">3</c:if>"><span
					style="font-size:12pt;"><bean:message
					key="main.matrixoverview.headline.tb.text" /></span></th>
			</tr>
			<tr>
				<c:if test="${view eq 'day'}">
					<th width="40%" class="matrix noBborderStyle" align="right"><c:out
						value="${currentDay}" />.</th>
					<th width="20%" class="matrix noBborderStyle" align="center"><bean:message
						key="${MonthKey}" /></th>
					<th width="40%" class="matrix noBborderStyle" align="left"><c:out
						value="${currentYear}" /></th>
				</c:if>
				<c:if test="${view eq 'month'}">
					<th width="50%" class="matrix noBborderStyle" align="right"><bean:message
						key="${MonthKey}" /></th>
					<th width="50%" class="matrix noBborderStyle" align="left"><c:out
						value="${currentYear}" /></th>
				</c:if>
				<c:if test="${view eq 'custom'}">
					<th width="45%" class="matrix noBborderStyle" nowrap="nowrap" align="right"><c:out
						value="${currentDay}" />. <bean:message
						key="${MonthKey}" /> <c:out
						value="${currentYear}" /></th>
					<th width="5%" class="matrix noBborderStyle" nowrap="nowrap" align="center"> - </th>	
					<th width="45%" class="matrix noBborderStyle" nowrap="nowrap" align="left"><c:out
						value="${lastDay}" />. <bean:message
						key="${LastMonthKey}" /> <c:out
						value="${lastYear}" /></th>
				</c:if>
			</tr>
			<tr>
				<!-- Employee -->
				<th class="matrix noBborderStyle" align="center" colspan="<c:if 
					test="${(view eq 'day')}">3</c:if><c:if test="${(view eq 'month')}">2</c:if><c:if test="${(view eq 'custom')}">3</c:if>"><c:if 
					test="${currentEmployee eq 'ALL EMPLOYEES'}"><bean:message
					key="main.matrixoverview.headline.allemployees.text" /></c:if>
					<c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}"><c:out
					value="${currentEmployee}" /></c:if></th>
			</tr>			
		</table>
		</center>
		<br>
		</th>
	</tr>
</table>
<table style="border:1px black solid;" class="matrix" width="100%">
	<tr class="matrix">
		<c:if test="${(currentEmployee eq 'ALL EMPLOYEES')}">
			<th class="matrix" align="left"
				title="<bean:message
			key="main.headlinedescription.dailyoverview.employee.text" />"><b><bean:message
				key="main.timereport.monthly.employee.text" /></b></th>
		</c:if>

		<th class="matrix" align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.refday.text" />"><b><bean:message
			key="main.timereport.monthly.refday.text" /></b></th>
		<th class="matrix" align="left" colspan="2"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.customerorder.text" />"><b><bean:message
			key="main.timereport.monthly.customerorder.text" /></b></th>
		<th class="matrix" align="left"
			width="<c:if test="${(currentEmployee eq 'ALL EMPLOYEES')}">40%</c:if><c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">45%</c:if>"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.taskdescription.text" />"><b><bean:message
			key="main.timereport.monthly.taskdescription.text" /></b></th>
		<th class="matrix" align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.training.text"/>"><b><bean:message
			key="main.timereport.monthly.training.text" /></b></th>
		<th class="matrix" align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.hours.text" />"><b><bean:message
			key="main.timereport.monthly.hours.text" /></b></th>
		<th class="matrix" align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.costs.text" />"><b><bean:message
			key="main.timereport.monthly.costs.text" /></b></th>
	</tr>

	<c:forEach var="timereport" items="${timereports}" varStatus="statusID">

		<html:form action="/UpdateDailyReport?trId=${timereport.id}">
			<tr class="matrix">
				<c:if test="${(currentEmployee eq 'ALL EMPLOYEES')}">
					<td class="matrix"><c:out
						value="${timereport.employeecontract.employee.lastname}" />,<br>
					<c:out value="${timereport.employeecontract.employee.firstname}" /></td>
				</c:if>
				<td class="matrix"
					title='<c:out value="${timereport.referenceday.name}" />'><logic:equal
					name="timereport" property="referenceday.holiday" value="true">
					<span style="color:red"> <bean:message
						key="${timereport.referenceday.dow}" /><br>
					<c:out value="${timereport.referenceday.refdate}" /> </span>
				</logic:equal> <logic:equal name="timereport" property="referenceday.holiday"
					value="false">
					<bean:message key="${timereport.referenceday.dow}" />
					<br>
					<c:out value="${timereport.referenceday.refdate}" />
				</logic:equal></td>
		
				<td class="matrix"
					title="<c:out value="${timereport.suborder.customerorder.description}"></c:out>">
				<c:out value="${timereport.suborder.customerorder.sign}"></c:out><br>
				<c:out value="${timereport.suborder.sign}"></c:out><br>
				</td>
				
				<td class="matrix" nowrap="nowrap">
					<c:out value="${timereport.suborder.customerorder.shortdescription}"></c:out><br>
					<c:out value="${timereport.suborder.shortdescription}"></c:out>
				</td>
				

				<!-- Kommentar -->
				<c:choose>
					<c:when test="${timereport.taskdescription==''}">
						<td class="matrix">&nbsp;</td>
					</c:when>
					<c:otherwise>
						<td class="matrix"><c:out
							value="${timereport.taskdescription}" /></td>
					</c:otherwise>
				</c:choose>
				
				<!-- Fortbildung -->
				<td align="center">
					<input type="checkbox" name="training" ${timereport.training ? 'checked' : '' } />  
				</td>


				<!-- Dauer -->
				<td class="matrix" align="center" nowrap="nowrap"><c:out
					value="${timereport.durationhours}" />:<c:if test="${!(timereport.durationminutes eq '0')}"><c:out
					value="${timereport.durationminutes}" /></c:if><c:if test="${timereport.durationminutes eq '0'}">00</c:if></td>

				<!-- Kosten -->
				<td class="matrix" align="center"><fmt:formatNumber value="${timereport.costs}" minFractionDigits="2"/></td>

			</tr>

		</html:form>
	</c:forEach>
	<tr class="matrix">
		<td
			colspan="<c:if test="${(currentEmployee eq 'ALL EMPLOYEES')}">5</c:if><c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">4</c:if>"
			class="noBborderStyle Matrix" align="right"><b><bean:message
			key="main.timereport.total.text" />:</b></td>
		<c:choose>
			<c:when test="${maxlabortime}">
				<th class="matrix" align="center" color="red"><b><font
					color="red"><c:out value="${labortime}"></c:out></font></b></th>
			</c:when>
			<c:otherwise>
				<th class="matrix" align="center"><b><c:out
					value="${labortime}"></c:out></b></th>
			</c:otherwise>
		</c:choose>
		<th class="matrix" align="center"><b>
		<fmt:formatNumber minFractionDigits="2"	value="${dailycosts}"/></b></th>
	</tr>
</table>
</body>
</html>
