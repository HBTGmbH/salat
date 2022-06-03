<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<html>
<head>
	<title><bean:message key="main.general.application.title" /> -
	<bean:message key="main.general.mainmenu.settings.title.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br>
<bean:message key="form.useraccesstoken.action.create" />:<br>
</span>
<br>
<html:form action="/StoreUserAccessToken">
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="form.useraccesstoken.header.comment" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:text property="comment" size="30" maxlength="100" />
				<span style="color:red"><html:errors property="comment" /></span>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="form.useraccesstoken.header.validuntil" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:text property="validUntil" size="30" />
				<span style="color:red"><html:errors property="validUntil" /></span>
			</td>
		</tr>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle">
				<html:submit styleId="button">
					<bean:message key="main.general.button.save.text" />
				</html:submit>
			</td>
		</tr>
	</table>
</html:form>

<c:if test="${generatedToken != null}">
	<table class="center backgroundcolor">
		<tr class="primarycolor">
			<td><bean:message key="form.useraccesstoken.header.tokenid" /></td>
			<td>${generatedToken.tokenId}</td>
		</tr>
		<tr class="secondarycolor">
			<td><bean:message key="form.useraccesstoken.header.tokensecret" /></td>
			<td>${generatedToken.tokenSecret}</td>
		</tr>
		<tr class="primarycolor">
			<td><bean:message key="form.useraccesstoken.header.validuntil" /></td>
			<td>${generatedToken.validUntil}</td>
		</tr>
		<tr class="secondarycolor">
			<td><bean:message key="form.useraccesstoken.header.comment" /></td>
			<td>${generatedToken.comment}</td>
		</tr>
	</table>
</c:if>

</body>
</html>
