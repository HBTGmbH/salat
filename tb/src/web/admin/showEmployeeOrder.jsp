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
<title><bean:message key="main.general.mainmenu.employeeorders.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployeeorder?eoId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.employeeorders.text"/></h2></p><br>
<br>

<span style="color:red"><html:errors /><br></span>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
		<td align="left"> <b><bean:message key="main.employeeorder.employee.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employeeorder.customerorder.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeeorder.suborder.text"/></b> </td>		
		<!--  
		<td align="left"> <b><bean:message key="main.employeeorder.sign.text"/></b> </td>	
		-->
		<td align="left"> <b><bean:message key="main.employeeorder.validfrom.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeeorder.validuntil.text"/></b> </td>	
		<td align="center"> <b><bean:message key="main.employeeorder.standingorder.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeeorder.debithours.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeeorder.status.text"/></b> </td>	
		<td align="center"> <b><bean:message key="main.employeeorder.statusreport.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeeorder.edit.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employeeorder.delete.text"/></b> </td>	
	</tr>

  	<logic:iterate id="employeeorder" name="employeeorders">
   	 <tr>
      	<td><bean:write name="employeeorder" property="employeecontract.employee.name"/></td>
      	<td><bean:write name="employeeorder" property="suborder.customerorder.sign"/></td>
      	<td><bean:write name="employeeorder" property="suborder.sign"/></td>
      	<!-- 
      	<td><bean:write name="employeeorder" property="sign"/></td>
      	 -->
      	<td><bean:write name="employeeorder" property="fromDate"/></td>
      	<td><bean:write name="employeeorder" property="untilDate"/></td>
      	<td align="center"><html:checkbox name="employeeorder" property="standingorder" disabled="true"/> </td>
      	<td><bean:write name="employeeorder" property="debithours"/></td>
      	<td><bean:write name="employeeorder" property="status"/></td>
      	<td align="center"><html:checkbox name="employeeorder" property="statusreport" disabled="true"/></td>
    
     	 <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditEmployeeorder?eoId=${employeeorder.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Employeeorder" /></html:link> </td>
     		<html:form action="/DeleteEmployeeorder">
     			<td align="center"> 
     				<html:image onclick="confirmDelete(this.form, ${employeeorder.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Employeeorder"/>
     			</td>
     		</html:form>
     	 </logic:equal>
   	 </tr>
 	</logic:iterate>
 
  <tr>
	 <html:form action="/CreateEmployeeorder">
		<td class="noBborderStyle" colspan="4">
			<html:submit>
				<bean:message key="main.general.button.createemployeeorder.text"/>
			</html:submit>
		</td>
	 </html:form>
  </tr>
</table>
<br><br>
	<table>
		<tr>
			<html:form action="/ShowEmployeeorder?task=back">
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
