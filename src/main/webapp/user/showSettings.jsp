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
<html:form action="/ShowSettings">
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">

		<tr>
			<td colspan="2" align="left" class="noBborderStyle">
			<h3><bean:message key="main.settings.changepassword.text" /></h3>
			</td>
			<td class="noBborderStyle"></td>
		</tr>
		<tr>
			<td colspan="2" align="left" class="noBborderStyle"><span
				style="font-size:10pt;font-weight:bold;"><bean:message
				key="main.settings.rule.text" /></span><br>
			<bean:message key="main.settings.rule1.text" /><br>
			<bean:message key="main.settings.rule3.text" /><br>
			<bean:message key="main.settings.rule4.text" /><br>
			&nbsp;&nbsp;<bean:message key="main.settings.rule41.text" /><br>
			&nbsp;&nbsp;<bean:message key="main.settings.rule42.text" /><br>
			&nbsp;&nbsp;<bean:message key="main.settings.rule43.text" /><br>
			&nbsp;&nbsp;<bean:message key="main.settings.rule44.text" /><br>
			</td>
			<td class="noBborderStyle"></td>
		</tr>
		<tr>
			<td colspan="2" align="left" class="noBborderStyle">&nbsp;</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.settings.password.old.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:password
				property="oldpassword" size="30" /> <span style="color:red"><html:errors
				property="oldpassword" /></span></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.settings.password.new.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:password
				property="newpassword" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="newpassword" /></span></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.settings.password.confirm.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:password
				property="confirmpassword" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors
				property="confirmpassword" /></span></td>
		</tr>

		<c:if test="${passwordchanged}">
			<tr>
				<td class="noBborderStyle"><br>
				</td>
			</tr>

			<tr>
				<td align="left" class="noBborderStyle"><i><bean:message
					key="main.settings.password.change.succesful.text" /></i></td>
			</tr>
		</c:if>

	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'changePassword');return false"
				styleId="button">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
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
