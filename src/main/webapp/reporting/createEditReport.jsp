<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<html:html>
<head>
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.reporting.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
</head>
<body>

<html:form action="/StoreReport">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.reporting.text" />:<br></span>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.reporting.name.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="name" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.REPORT_NAME_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="name" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
					key="main.reporting.sql.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<html:textarea property="sql" cols="80" rows="20" />
				<span style="color:red">
					<html:errors property="sql" />
				</span>
			</td>
		</tr>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle">
				<c:if test="${createEditReport_authorizedToStore}">
					<html:submit styleId="button">
						<bean:message key="main.reporting.button.store.text" />
					</html:submit>
				</c:if>
			</td>
		</tr>
	</table>
	<html:hidden property="mode" />
	<html:hidden property="reportId" />
</html:form>
<h2>Authorizations</h2>
<table>
	<thead>
		<tr>
			<th>User</th>
			<th>Access Level</th>
			<th>Validity</th>
		</tr>
	</thead>
	<tbody>
		<c:forEach items="${reportAuthorizations}" var="reportAuthorization">
			<tr>
				<td>${reportAuthorization.userSign}</td>
				<td>${reportAuthorization.accessLevel}</td>
				<td>${reportAuthorization.validity}</td>
			</tr>
		</c:forEach>
	</tbody>
</table>
</body>
</html:html>
