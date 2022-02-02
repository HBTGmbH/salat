<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<html:html>
<head>
<html:base />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addemployee.text" /></title>
<link rel="stylesheet" type="text/css" href="../style/tb.css" />
<link rel="shortcut icon" type="image/x-icon" href="../favicon.ico" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "../do/StoreEmployee?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}	
			
</script>

</head>
<body>

<html:form action="/StoreEmployee">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.enteremployeeproperties.text" />:<br></span>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.firstname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="firstname" size="30"
				maxlength="<%=\"\" + org.tb.GlobalConstants.EMPLOYEE_FIRSTNAME_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="firstname" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.lastname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="lastname" size="30"
				maxlength="<%=\"\" + org.tb.GlobalConstants.EMPLOYEE_LASTNAME_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="lastname" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.sign.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="sign" size="30"
				maxlength="<%=\"\" + org.tb.GlobalConstants.EMPLOYEE_SIGN_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="sign" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.loginname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="loginname" size="30"
				maxlength="<%=\"\" + org.tb.GlobalConstants.EMPLOYEE_LOGINNAME_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="loginname" /></span></td>
		</tr>

		

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.status.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="status" >
				<html:option value="ma">
					<bean:message key="main.employee.status.ma" />
				</html:option>
				<!-- 
				<html:option value="av">
					<bean:message key="main.employee.status.av" />
				</html:option>
				<html:option value="pl">
					<bean:message key="main.employee.status.pl" />
				</html:option>
				-->
				<html:option value="bl">
					<bean:message key="main.employee.status.bl" />
				</html:option>
				<html:option value="pv">
					<bean:message key="main.employee.status.pv" />
				</html:option>
				<html:option value="restricted">
					<bean:message key="main.employee.status.restricted" />
				</html:option>
				<html:option value="adm">
					<bean:message key="main.employee.status.adm" />
				</html:option>
			</html:select></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.gender.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="gender" >
				<html:option value="m">
					<bean:message key="main.general.employee.gender.male" />
				</html:option>
				<html:option value="f">
					<bean:message key="main.general.employee.gender.female" />
				</html:option>
			</html:select></td>
		</tr>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button"  titleKey="main.general.button.save.alttext.text">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button" titleKey="main.general.button.saveandcontinue.alttext.text">
				<bean:message key="main.general.button.saveandcontinue.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button" titleKey="main.general.button.reset.alttext.text">
				<bean:message key="main.general.button.reset.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'resetPassword', 'false')" styleId="button">
				<bean:message key="main.general.button.resetpassword.text" />
			</html:submit></td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>
</html:html>
