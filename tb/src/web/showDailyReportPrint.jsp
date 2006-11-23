<%@ page import="org.tb.bdom.Employee"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%
            Double hourBalance = (Double)request.getSession().getAttribute("hourbalance");
            int displayLength = 0;
            for (int i = 0; i < hourBalance.toString().length(); i++) {
                if (hourBalance.toString().charAt(i) == '.') {
                    displayLength = Math.min(i + 3, hourBalance.toString().length());
                    break;
                }
            }
            String hourBalanceDisplay = hourBalance.toString().substring(0, displayLength);

            String vacation = (String)request.getSession().getAttribute("vacation");
%>
<%
Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.mainmenu.daily.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/preview.css"
	media="all" />
<link rel="stylesheet" type="text/css" href="/tb/print.css"
	media="print" />

</head>
<body>
<c:out value="${currentEmployee}" /> / 
<c:out value="${currentOrder}" /> / 
<c:out value="${currentDay}" />. 
<c:out value="${currentMonth}" /> 
<c:out value="${currentYear}" />
<table class="center">

	<tr>
		<td colspan="5" class="noBborderStyle">&nbsp;</td>
		<td class="noBborderStyle" align="right"><b><bean:message
			key="main.timereport.total.text" />:</b></td>
		<c:choose>
			<c:when test="${maxlabortime}">
				<th align="center" color="red"><b><font color="red"><c:out
					value="${labortime}"></c:out></font></b></th>
			</c:when>
			<c:otherwise>
				<th align="center"><b><c:out value="${labortime}"></c:out></b>
				</th>
			</c:otherwise>
		</c:choose>
		<th align="center"><b><c:out value="${dailycosts}"></c:out></b></th>
	</tr>

	<tr>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.employee.text" />"><b><bean:message
			key="main.timereport.monthly.employee.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.refday.text" />"><b><bean:message
			key="main.timereport.monthly.refday.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.sortofreport.text" />"><b><bean:message
			key="main.timereport.monthly.sortofreport.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.customerorder.text" />"><b><bean:message
			key="main.timereport.monthly.customerorder.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.suborder.text" />"><b><bean:message
			key="main.timereport.monthly.suborder.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.taskdescription.text" />"
			width="25%"><b><bean:message
			key="main.timereport.monthly.taskdescription.text" /></b></th>
		<th align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.hours.text" />"><b><bean:message
			key="main.timereport.monthly.hours.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.costs.text" />"><b><bean:message
			key="main.timereport.monthly.costs.text" /></b></th>
		<!--  
		<th align="left"> <b><bean:message key="main.timereport.monthly.status.text"/></b> </th>	
		-->
	</tr>

	<c:forEach var="timereport" items="${timereports}" varStatus="statusID">

		<html:form action="/UpdateDailyReport?trId=${timereport.id}">
			<tr>
				<td><c:out value="${timereport.employeecontract.employee.name}" /></td>
				<td title='<c:out value="${timereport.referenceday.name}" />'><logic:equal
					name="timereport" property="referenceday.holiday" value="true">
					<span style="color:red"> <bean:write name="timereport"
						property="referenceday.dow" /> <bean:write name="timereport"
						property="referenceday.refdate" /> </span>
				</logic:equal> <logic:equal name="timereport" property="referenceday.holiday"
					value="false">
					<c:out value="${timereport.referenceday.dow}" />
					<c:out value="${timereport.referenceday.refdate}" />
				</logic:equal></td>
				<td align="center"><logic:equal name="timereport"
					property="sortofreport" value="W">
					<bean:message key="main.timereport.monthly.sortofreport.work.text" />
				</logic:equal> <logic:equal name="timereport" property="sortofreport" value="V">
					<bean:message
						key="main.timereport.monthly.sortofreport.vacation.text" />
				</logic:equal> <logic:equal name="timereport" property="sortofreport" value="S">
					<bean:message key="main.timereport.monthly.sortofreport.sick.text" />
				</logic:equal></td>
				<td
					title="<c:out value="${timereport.suborder.customerorder.description}"></c:out>">
				<c:out value="${timereport.suborder.customerorder.sign}"></c:out><br>
				</td>
				<td
					title="<c:out value="${timereport.suborder.description}"></c:out>">
				<c:out value="${timereport.suborder.sign}"></c:out><br>
				</td>

				<!-- visibility dependent on user and status -->

				<c:choose>
					<c:when
						test="${((loginEmployee.name == currentEmployee) && (timereport.status == 'open')) || ((loginEmployee.status == bl) && (timereport.status == 'commited'))}">

						<!-- Kommentar -->
						<c:choose>
							<c:when test="${timereport.taskdescription==''}">
								<td>&nbsp;</td>
							</c:when>
							<c:otherwise>
								<td><c:out value="${timereport.taskdescription}" /></td>
							</c:otherwise>
						</c:choose>


						<!-- Dauer -->
						<td align=center nowrap><c:out
							value="${timereport.durationhours}" /> : <c:out
							value="${timereport.durationminutes}" /></td>

						<!-- Kosten -->
						<td><c:out value="${timereport.costs}" /></td>
					</c:when>
					<c:otherwise>

						<!-- Kommentar -->
						<c:choose>
							<c:when test="${timereport.taskdescription==''}">
								<td>&nbsp;</td>
							</c:when>
							<c:otherwise>
								<td><c:out value="${timereport.taskdescription}" /></td>
							</c:otherwise>
						</c:choose>

						<!-- Dauer -->
						<td align="center" nowrap><c:out
							value="${timereport.durationhours}" />:<c:out
							value="${timereport.durationminutes}" /></td>

						<!-- Kosten -->
						<td align="center"><c:out value="${timereport.costs}" /></td>
					</c:otherwise>
				</c:choose>
			</tr>

		</html:form>
	</c:forEach>
	<tr>
		<td colspan="5" class="noBborderStyle">&nbsp;</td>
		<td class="noBborderStyle" align="right"><b><bean:message
			key="main.timereport.total.text" />:</b></td>
		<c:choose>
			<c:when test="${maxlabortime}">
				<th align="center" color="red"><b><font color="red"><c:out
					value="${labortime}"></c:out></font></b></th>
			</c:when>
			<c:otherwise>
				<th align="center"><b><c:out value="${labortime}"></c:out></b>
				</th>
			</c:otherwise>
		</c:choose>
		<th align="center"><b><c:out value="${dailycosts}"></c:out></b></th>
	</tr>
</table>
<FORM ONSUBMIT="javascript:window.print();return false;"><input
	class="hiddencontent" type="submit" value="Drucken"></form>
</body>
</html>
