<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
	<bean:message key="main.general.mainmenu.training.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" media="all" />
<link rel="stylesheet" type="text/css" href="/tb/print.css"
	media="print" />
<script type="text/javascript" language="JavaScript">
	function setUpdate(form) {
		form.action = "/tb/do/ShowTraining?task=refresh";
		form.submit();
	}
</script>
</head>

<body>
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size: 14pt; font-weight: bold;"> <br> <bean:message key="main.general.mainmenu.training.text" /> 
	</span>
	<br>
	<br>
	<b><font color="red"><bean:message key="main.training.textwarning" /></font></b> <br>
	<br>
	<html:form action="/ShowTraining">
		<table class="center backgroundcolor">
			<colgroup>
				<col align="left" width="185" />
				<col align="left" width="750" />
			</colgroup>

			<!-- select employeecontract -->
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
							key="main.monthlyreport.employee.fullname.text" />:</b></td>
				<td align="left" class="noBborderStyle" nowrap="nowrap"><html:select
						property="employeeContractId"
						value="${currentEmployeeContract.id}"
						onchange="setUpdate(this.form)">
						<html:option value="-1">
							<bean:message key="main.general.allemployees.text" />
						</html:option>
						<c:forEach var="employeecontracts" items="${employeecontracts}">
							<c:if
								test="${employeecontracts.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
								<html:option value="${employeecontracts.id}">
									<c:out value="${employeecontracts.employee.sign}" /> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
										value="${employeecontracts.timeString}" />
									<c:if test="${employeecontracts.openEnd}">
										<bean:message key="main.general.open.text" />
									</c:if>)
									</html:option>
							</c:if>
						</c:forEach>
					</html:select></td>
			</tr>


			<!--  select year -->
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
							key="main.training.year" />:</b></td>
				<td align="left" class="noBborderStyle" nowrap="nowrap"><html:select
						property="year" value="${year}" onchange="setUpdate(this.form)">
						<html:options collection="years" property="value"
							labelProperty="label" />
					</html:select></td>
			</tr>
		</table>



		<table class="center backgroundcolor">
			<tr>
				<th style="visibility:hidden"/>
				<th align="left" colspan="2"
					title="<bean:message key='main.headlinedescription.training.commontraining.text' />">
					<b><bean:message key="main.training.commontraining.text" /></b>
				</th>
				<th align="left" colspan="2"
					title="<bean:message key='main.headlinedescription.training.projecttraining.text' />">
					<b><bean:message key="main.training.projecttraining.text" /></b>
				</th>
			</tr>
			
			<tr>
				<th align="left"
					title="<bean:message key="main.headlinedescription.employeecontracts.employeename.text" />"><b><bean:message
							key="main.employeecontract.employee.text" /></b></th>
				<th align="left"
					title="<bean:message key='main.headlinedescription.training.commontraining.text' />">
					<b><bean:message key="main.training.text1" /></b>
				</th>
				<th align="left"
					title="<bean:message key='main.headlinedescription.training.commontraining.text' />">
					<b><bean:message key="main.training.text2" /></b>
				</th>
				<th align="left"
					title="<bean:message key='main.headlinedescription.training.projecttraining.text' />">
					<b><bean:message key="main.training.text1" /></b>
				</th>
				<th align="left"
					title="<bean:message key='main.headlinedescription.training.projecttraining.text' />">
					<b><bean:message key="main.training.text2" /></b>
				</th>
			</tr>					

			<c:forEach var="training" items="${trainingOverview}"
				varStatus="statusID">
				<c:choose>
					<c:when test="${statusID.count%2 == 0}">
						<tr class="primarycolor">
					</c:when>
					<c:otherwise>
						<tr class="secondarycolor">
					</c:otherwise>
				</c:choose>



				<!-- Name -->
				<td><c:out value="${training.employeecontract.employee.name}" /></td>

				<!-- common training -->
				<td title="<bean:message key="main.training.title.text" />">
					<b><c:out value="${training.commonTrainingTime}"></c:out></b>
				</td>
				<td title="<bean:message key="main.training.title.text2" />">
					<b><c:out value="${training.cTTHoursMin}"></c:out></b>
				</td>
								
				<!-- projectbased training -->
				<td	title="<bean:message key="main.training.title.text" />">
					<b><c:out value="${training.projectTrainingTime}"></c:out></b>
				</td>
				<td title="<bean:message key="main.training.title.text2" />">
					<b><c:out value="${training.pTTHoursMin}"></c:out></b>
				</td>
				
			</c:forEach>
		</table>
	</html:form>
</body>
</html:html>

