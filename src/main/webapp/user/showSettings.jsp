<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<html>
<head>
	<title><bean:message key="main.general.application.title" /> -
	<bean:message key="main.general.mainmenu.settings.title.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
	<script type="text/javascript" language="JavaScript">
		function setStoreAction(form, actionVal) {
			form.action = "/do/ShowSettings?task=" + actionVal;
			form.submit();
		}
	</script>
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br>
<bean:message key="main.general.mainmenu.settings.title.text" />:<br>
</span>
<br>

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

<table class="center backgroundcolor">
	<tr>
		<th><bean:message key="form.useraccesstoken.header.tokenid" /></th>
		<th><bean:message key="form.useraccesstoken.header.validuntil" /></th>
		<th><bean:message key="form.useraccesstoken.header.comment" /></th>
		<th></th>
	</tr>
<c:forEach var="userAccessToken" items="${userAccessTokens}" varStatus="status">
	<tr class="${status.count%2==0 ? 'primarycolor': 'secondaryColor'}">
		<td>${userAccessToken.tokenId}</td>
		<td>${userAccessToken.validUntil}</td>
		<td>${userAccessToken.comment}</td>
		<td><a href="/do/DeleteUserAccessToken?id=${userAccessToken.id}"><bean:message key="form.useraccesstoken.action.delete" /></a></td>
	</tr>
</c:forEach>
	<tr>
		<th colspan="4"><a href="/do/CreateUserAccessToken"><bean:message key="form.useraccesstoken.action.create" /></a></th>
	</tr>
</table>

</body>
</html>
