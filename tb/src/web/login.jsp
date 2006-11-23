<%@ page
	language="java"
	contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%
java.util.Enumeration names = session.getAttributeNames();
while(names.hasMoreElements()) {
	String name = (String) names.nextElement();
	session.removeAttribute(name);
}
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html:html>
<head>
<meta
	http-equiv="Content-Type"
	content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.title.text" /></title>
<link
	rel="stylesheet"
	type="text/css"
	href="/tb/tb.css" />
</head>
<body>
<b>
<span style="color:red">
<html:errors />
</span>
</b>
<html:form action="/LoginEmployee" focus="loginname">
	<table align="center" cellspacing="10">
		<tr>
			<td
				colspan="1"
				class="noBborderStyle"
				height="100px">
				<!-- 
				<img
				src="/tb/images/hbtlogo_halb.jpg"
				width="130"
				height="60"
				border="0">
				 -->
			</td>
			<td class="noBborderStyle">
				<b><bean:message key="main.general.loginscreen.text" /> </b>
			</td>
		</tr>
		<tr></tr>
		<tr></tr>
		<tr>
			<td class="noBborderStyle"><bean:message key="main.general.employeesign.text" />:</td>
			<td class="noBborderStyle"><html:text
				property="loginname"
				maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_LOGINNAME_MAX_LENGTH %>" /></td>
		</tr>
		<tr>
			<td class="noBborderStyle"><bean:message key="main.general.password.text" />:</td>
			<td class="noBborderStyle"><html:password
				property="password"
				maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH %>" /></td>
		</tr>
		<tr>
			<td class="noBborderStyle">&nbsp;</td>
			<td class="noBborderStyle">
			<html:submit styleId="button"> 
				<bean:message key="main.general.loginsubmit.text"/>
			</html:submit>	
			</td>
		</tr>
		<tr></tr>
		<tr>
			<td class="noBborderStyle">
				<img src="/tb/images/flag_en_GB.gif" width="36" height="24" border="0">
			</td>
			<td class="noBborderStyle">
				<bean:message key="main.general.loginscreen.en.1"/><br>
				<bean:message key="main.general.loginscreen.en.2"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle">
				<img src="/tb/images/flag_de_DE.gif" width="36" height="24" border="0">
			</td>
			<td class="noBborderStyle">
				<bean:message key="main.general.loginscreen.de.1"/><br>
				<bean:message key="main.general.loginscreen.de.2"/>
			</td>
		</tr>
		<tr height="30px"></tr>
		<tr>
			<td valign="bottom" align="left" class="noBborderStyle">
				<!--  
				<img src="/tb/images/Button/Sanduhr_1.gif" border="0">
				-->
				Version 1.18.6
			</td>
		</tr>
		<tr height="10px"/>
		<tr><td colspan="2" class="noBborderStyle"><font color="red"> </font>
		</td></tr> 
	</table>
</html:form>
</body>
</html:html>
