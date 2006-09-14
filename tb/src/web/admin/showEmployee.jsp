<%@ page import="org.tb.bdom.Employee" %>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%
%>

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.mainmenu.employees.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployee?emId=" + id;
			form.submit();
		}
	}					
 
</script>


</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.employees.text"/></h2></p><br><br>

<span style="color:red"><html:errors /><br></span>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
		<td align="left"> <b><bean:message key="main.employee.firstname.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employee.lastname.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employee.sign.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employee.loginname.text"/></b> </td>
		<!--  
		<td align="left"> <b><bean:message key="main.employee.password.text"/></b> </td>
		-->
		<td align="left"> <b><bean:message key="main.employee.status.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employee.gender.text"/></b> </td>		
		<td align="left"> <b><bean:message key="main.employee.edit.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employee.delete.text"/></b> </td>	
	</tr>
  <logic:iterate id="employee" name="employees">
    <tr>
      	<td><bean:write name="employee" property="firstname"/></td>
      	<td><bean:write name="employee" property="lastname"/></td>
      	<td><bean:write name="employee" property="sign"/></td>
      	<td><bean:write name="employee" property="loginname"/></td>
      	<!--  
      	<td><bean:write name="employee" property="password"/></td>
      	-->
      	<td><bean:write name="employee" property="status"/></td>
      	<td align="center"><bean:write name="employee" property="gender"/></td>
    
     	 <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditEmployee?emId=${employee.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Employee" /></html:link> </td>
     		<html:form action="/DeleteEmployee">
     		 	<td align="center"> 
     				<html:image onclick="confirmDelete(this.form, ${employee.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Employee"/>
     			</td>
     		</html:form>
     	 </logic:equal>
    </tr>
  </logic:iterate>
  <tr>
	 <html:form action="/CreateEmployee">
		<td class="noBborderStyle" colspan="4">
			<html:submit>
				<bean:message key="main.general.button.createemployee.text"/>
			</html:submit>
		</td>
	 </html:form>
  </tr>
</table>
<br><br>
	<table>
		<tr>
			<html:form action="/ShowEmployee?task=back">
				<td class="noBborderStyle">        
        			<html:submit>
        				<bean:message key="main.general.button.backmainmenu.text"/>
        			</html:submit>
        		</td>
			</html:form>
		</tr>

	</table>
	
<br>
<br>
	<table class="center">
        <tr>
			<td class="noBborderStyle"> 
			<html:form action="/LogoutEmployee">
        		<html:submit>
        			<bean:message key="main.general.logout.text"/>
        		</html:submit>
        	</html:form>
			</td>
		</tr>
	</table>
</body>
</html:html>
