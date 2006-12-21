<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message
	key="main.general.mainmenu.settings.title.text" /></title>

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/ShowSettings?task=" + actionVal;
		form.submit();
	}	
			
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
	<p>
	<h2><bean:message key="main.general.mainmenu.settings.title.text" />:</h2>
	</p>
	<br>
	<html:form action="/ShowSettings">
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		
		<tr>
			<td align="left" class="noBborderStyle"><h3><bean:message
				key="main.settings.changepassword.text" /></h3></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.settings.password.old.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:password
				property="oldpassword" size="30" />
			<span style="color:red"><html:errors property="oldpassword" /></span></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.settings.password.new.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:password
				
				property="newpassword" size="30"
				maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="newpassword" /></span></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.settings.password.confirm.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:password
				property="confirmpassword" size="30"
				maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="confirmpassword" /></span></td>
		</tr>
		
		<c:if test="${passwordchanged}">
			<tr><td class="noBborderStyle"><br></td></tr>
		
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
				onclick="setStoreAction(this.form, 'changePassword');return false" styleId="button">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
		</tr>
	</table>
</html:form>
</body>
</html>
