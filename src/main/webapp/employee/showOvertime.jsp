<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
	<head>
		<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.employees.text" /></title>
		<jsp:include flush="true" page="/head-includes.jsp" />
		<script type="text/javascript" language="JavaScript">
			function refresh(form) {
				form.action = "/do/ShowOvertime?task=refresh";
				form.submit();
			}
			function correct_overtime(form) {
				form.action = "/do/ShowOvertime?task=correct-overtime";
				form.submit();
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
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.overtime.text" /><br></span>
	<br>
	<span style="color:red"><html:errors footer="<br>" /> </span>

	<table class="center backgroundcolor">
		<html:form action="/ShowOvertime?task=refresh">
			<tr>
				<td class="noBborderStyle" colspan="2">
					<b><bean:message key="main.overtime.employeecontract.text" /></b>
				</td>
				<td class="noBborderStyle" align="left">
					<html:select property="employeecontractId" onchange="refresh(this.form)" styleClass="make-select2">
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
				<td class="noBborderStyle"><html:submit onclick="correct_overtime(this.form)"><bean:message key="main.overtime.employeecontract.correct.label" /></html:submit></td>
			</tr>
		</html:form>
	</table>

	<table class="center backgroundcolor">
		<tr>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.yearmonth.text" /></b></th>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.actual.text" /></b></th>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.adjustment.text" /></b></th>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.sum.text" /></b></th>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.target.text" /></b></th>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.diff.text" /></b></th>
			<th align="left"><b><bean:message key="main.headlinedescription.overtime.report.diffcumulative.text" /></b></th>
		</tr>
		<tr>
			<td style="text-align: center"><b><bean:message key="main.headlinedescription.overtime.report.total.text" /></b></td>
			<td style="text-align: right"><b><java8:formatDuration value="${overtimereport.total.actual}" /></b></td>
			<td style="text-align: right"><b><java8:formatDuration value="${overtimereport.total.adjustment}" /></b></td>
			<td style="text-align: right"><b><java8:formatDuration value="${overtimereport.total.sum}" /></b></td>
			<td style="text-align: right"><b><java8:formatDuration value="${overtimereport.total.target}" /></b></td>
			<c:choose>
				<c:when test="${overtimereport.total.diff.negative}">
					<td style="text-align: right; color: red"><b><java8:formatDuration value="${overtimereport.total.diff}" /></b></td>
				</c:when>
				<c:otherwise>
					<td style="text-align: right"><b><java8:formatDuration value="${overtimereport.total.diff}" /></b></td>
				</c:otherwise>
			</c:choose>
			<c:choose>
				<c:when test="${overtimereport.total.diffCumulative.negative}">
					<td style="text-align: right; color: red"><b><java8:formatDuration value="${overtimereport.total.diffCumulative}" /></b></td>
				</c:when>
				<c:otherwise>
					<td style="text-align: right"><b><java8:formatDuration value="${overtimereport.total.diffCumulative}" /></b></td>
				</c:otherwise>
			</c:choose>
		</tr>
		<c:forEach var="month" items="${overtimereport.months}" varStatus="statusID">
			<c:choose>
				<c:when test="${statusID.count%2==0}">
					<tr class="primarycolor">
				</c:when>
				<c:otherwise>
					<tr class="secondarycolor">
				</c:otherwise>
			</c:choose>
				<td style="text-align: center"><java8:formatYearMonth value="${month.yearMonth}" /></td>
				<td style="text-align: right"><java8:formatDuration value="${month.actual}" /></td>
				<td style="text-align: right"><java8:formatDuration value="${month.adjustment}" /></td>
				<td style="text-align: right"><java8:formatDuration value="${month.sum}" /></td>
				<td style="text-align: right"><java8:formatDuration value="${month.target}" /></td>
				<c:choose>
					<c:when test="${month.diff.negative}">
						<td style="text-align: right; color: red"><java8:formatDuration value="${month.diff}" /></td>
					</c:when>
					<c:otherwise>
						<td style="text-align: right"><java8:formatDuration value="${month.diff}" /></td>
					</c:otherwise>
				</c:choose>
				<c:choose>
					<c:when test="${month.diffCumulative.negative}">
						<td style="text-align: right; color: red"><java8:formatDuration value="${month.diffCumulative}" /></td>
					</c:when>
					<c:otherwise>
						<td style="text-align: right"><java8:formatDuration value="${month.diffCumulative}" /></td>
					</c:otherwise>
				</c:choose>
			</tr>
		</c:forEach>
	</table>

	<br><br><br>
	</body>
</html:html>
