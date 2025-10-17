<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<html:html>
<head>
<title><bean:message key="main.general.application.title" /> - Scheduled Report Job</title>
	<jsp:include flush="true" page="/head-includes.jsp" />
</head>
<body>

<html:form action="/StoreScheduledReportJob">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br>Scheduled Report Job:<br></span>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b>Job Name</b></td>
			<td align="left" class="noBborderStyle">
				<html:text property="name" size="50" maxlength="255" />
				<span style="color:red"><html:errors property="name" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b>Report</b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="reportDefinitionId" styleId="reportDefinitionId">
					<html:option value="">-- Select Report --</html:option>
					<c:forEach var="report" items="${reportDefinitions}">
						<html:option value="${report.id}">
							<c:out value="${report.name}" />
						</html:option>
					</c:forEach>
				</html:select>
				<span style="color:red"><html:errors property="reportDefinitionId" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b>Report Parameters (JSON)</b></td>
			<td align="left" class="noBborderStyle">
				<html:textarea property="reportParameters" cols="60" rows="5" />
				<br>
				<small>Example: {"startDate": "2024-01-01", "endDate": "2024-12-31"}</small>
				<br>
				<span style="color:red"><html:errors property="reportParameters" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b>Recipient Emails</b></td>
			<td align="left" class="noBborderStyle">
				<html:textarea property="recipientEmails" cols="60" rows="3" />
				<br>
				<small>Comma or semicolon separated email addresses</small>
				<br>
				<span style="color:red"><html:errors property="recipientEmails" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b>Enabled</b></td>
			<td align="left" class="noBborderStyle">
				<html:checkbox property="enabled" value="true" />
				<span style="color:red"><html:errors property="enabled" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b>Cron Expression (optional)</b></td>
			<td align="left" class="noBborderStyle">
				<html:text property="cronExpression" size="30" maxlength="255" />
				<br>
				<small>Leave empty for default schedule (Daily at 2:00 AM)</small>
				<br>
				<span style="color:red"><html:errors property="cronExpression" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b>Description</b></td>
			<td align="left" class="noBborderStyle">
				<html:textarea property="description" cols="60" rows="3" />
				<span style="color:red"><html:errors property="description" /></span>
			</td>
		</tr>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle">
				<html:submit styleId="button">
					Save Scheduled Job
				</html:submit>
			</td>
			<td class="noBborderStyle">
				<html:link action="/ShowScheduledReportJobs">
					<html:button property="cancel" styleId="button">
						Cancel
					</html:button>
				</html:link>
			</td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>
</html:html>
