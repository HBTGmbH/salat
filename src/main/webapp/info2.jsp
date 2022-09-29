<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<table border="0" cellspacing="2px" cellpadding="0" width="100%"
		class="noBborderStyle center backgroundcolor">
	<tr class="noBborderStyle">
		<th class="noBborderStyle" align="left" width="33%">
			<b><bean:message
				key="main.welcome.reports.text" /></b>
		</th>
		<th class="noBborderStyle" align="left" width="33%">
			<b><bean:message
				key="main.welcome.overtime.text" /></b>
		</th>
		<th class="noBborderStyle" align="left" width="33%">
			<b><bean:message
				key="main.welcome.vacation.text" /></b>
		</th>
	</tr>	
	<tr class="noBborderStyle">
		<td align="left" valign="top" class="noBborderStyle">
			<table border="0" cellspacing="0" cellpadding="0" 
				class="center backgroundcolor">
				<!-- reports -->
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.release.released.until.text" />:</b></td>
					<td align="left" class="noBborderStyle">
						<b>	
							<c:choose>
								<c:when test="${releaseWarning}">
									<font color="red">
										<c:out value="${releasedUntil}" />
									</font>
								</c:when>
								<c:otherwise>
									<c:out value="${releasedUntil}" />
								</c:otherwise>
							</c:choose>	
						</b>	
					</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.release.accepted.until.text" />:</b></td>
					<td align="left" class="noBborderStyle">
						<b>	
							<c:choose>
								<c:when test="${acceptanceWarning}">
									<font color="red">
										<c:out value="${acceptedUntil}" />
									</font>
								</c:when>
								<c:otherwise>
									<c:out value="${acceptedUntil}" />
								</c:otherwise>
							</c:choose>	
						</b>
					</td>
				</tr>			
			</table>
		</td>
		<td align="left" valign="top" class="noBborderStyle">
			<table border="0" cellspacing="0" cellpadding="0"
				class="center backgroundcolor">
				<!-- overtime -->
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
				<!--  only for showDailyReport: overtime until (end-)date-->
				<c:if test="${showOvertimeUntil}">
					<tr>
						<td align="left" class="noBborderStyle"><b>bis <c:out value="${enddate}"/>: </b></td>
						<c:choose>
							<c:when test="${overtimeUntilIsNeg}">
								<td align="left" class="noBborderStyle"><b><font color="red"><c:out value="${overtimeUntil}" /></font></b></td>
							</c:when>
							<c:otherwise>
								<td align="left" class="noBborderStyle"><b><c:out value="${overtimeUntil}" /></b></td>
							</c:otherwise>
						</c:choose>
					</tr>		
				</c:if>	
			</table>
		</td>
		<td align="left" valign="top" class="noBborderStyle">
			<table border="0" cellspacing="0" cellpadding="0"
				class="center backgroundcolor">
				<!-- vacation -->
				<c:forEach var="vacationentry" items="${vacations}">
					<tr>
						<td align="left" class="noBborderStyle">
							<b><c:out value="${vacationentry.suborderSign}" />:</b>
						</td>
						<c:choose>
							<c:when test="${vacationentry.vacationBudgetExceeded}">
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
			</table>
		</td>
	</tr>
</table>
