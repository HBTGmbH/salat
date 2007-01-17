<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">

<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
	<tr>
		<th align="left" colspan="4">
			<b><bean:message
				key="main.welcome.overtime.text" /></b>
		</th>
	</tr>
	<tr>
		<td align="left" class="noBborderStyle"><b><bean:message
				key="main.welcome.total.text" />: </b></td>
		<c:choose>
			<c:when test="${overtimeIsNegative}">
				<td align="left" class="noBborderStyle"><b><font color="red"><c:out value="${overtime}" /></font></b></td>
			</c:when>
			<c:otherwise>
				<td align="left" class="noBborderStyle"><b><c:out value="${overtime}" /></b></td>
			</c:otherwise>
		</c:choose>
	</tr>
	<tr>
		<td align="left" class="noBborderStyle"><b><c:out value="${overtimeMonth}" />: </b></td>
		<c:choose>
			<c:when test="${monthlyOvertimeIsNegative}">
				<td align="left" class="noBborderStyle"><b><font color="red"><c:out value="${monthlyOvertime}" /></font></b></td>
			</c:when>
			<c:otherwise>
				<td align="left" class="noBborderStyle"><b><c:out value="${monthlyOvertime}" /></b></td>
			</c:otherwise>
		</c:choose>
	</tr>
	<tr>
		<td align="left" class="noBborderStyle">
			&nbsp;
		</td>
	</tr>
	<tr>
		<th align="left" colspan="4">
			<b><bean:message
				key="main.welcome.vacation.text" /></b>
		</th>
	</tr>
	<c:forEach var="vacationentry" items="${vacations}">
		<tr>
			<td align="left" class="noBborderStyle">
				<b><c:out value="${vacationentry.suborderSign}" />:</b>
			</td>
			<c:choose>
				<c:when test="${vacationentry.extended}">
					<td align="left" class="noBborderStyle" title="<bean:message
						key="main.welcome.vacation.title.text" />"><b><font color="red">
							<c:out value="${vacationentry.usedVacationString}" /></font>
							&nbsp;/&nbsp;&nbsp;<c:out value="${vacationentry.budgetVacationString}" /></b></td>
				</c:when>
				<c:otherwise>
					<td align=left class="noBborderStyle" title="<bean:message
						key="main.welcome.vacation.title.text" />"><b>
							<c:out value="${vacationentry.usedVacationString}" />
							&nbsp;/&nbsp;&nbsp;<c:out value="${vacationentry.budgetVacationString}" /></b></td>
				</c:otherwise>
			</c:choose>
		</tr>
	</c:forEach>
	<tr>
		<td align="left" class="noBborderStyle">
			&nbsp;
		</td>
	</tr>
	<tr>
		<th align="left" colspan="4">
			<b><bean:message
				key="main.welcome.reports.text" /></b>
		</th>
	</tr>
	<tr>
		<td align="left" class="noBborderStyle"><b><bean:message
			key="main.release.released.until.text" />:</b></td>
		<td align="left" class="noBborderStyle">
			<b><c:out value="${releasedUntil}" /></b>	
		</td>
	</tr>
	<tr>
		<td align="left" class="noBborderStyle"><b><bean:message
			key="main.release.accepted.until.text" />:</b></td>
		<td align="left" class="noBborderStyle">
			<b><c:out value="${acceptedUntil}" /></b>	
		</td>
	</tr>
</table>