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
<title><bean:message key="main.general.mainmenu.employeecontracts.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployeecontract?ecId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.employeecontracts.text"/></h2></p><br><br>

<span style="color:red"><html:errors /><br></span>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
		<td align="left"> <b><bean:message key="main.employeecontract.employee.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employeecontract.taskdescription.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeecontract.validfrom.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeecontract.validuntil.text"/></b> </td>	
		<td align="center"> <b><bean:message key="main.employeecontract.freelancer.text"/></b> </td>	
		<td align="center"> <b><bean:message key="main.employeecontract.dailyworkingtime.text"/></b> </td>	
		<td align="center"> <b><bean:message key="main.employeecontract.yearlyvacation.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.employeecontract.edit.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.employeecontract.delete.text"/></b> </td>	
	</tr>

  	<logic:iterate id="employeecontract" name="employeecontracts">
   	 <tr>
      	<td><bean:write name="employeecontract" property="employee.name"/></td>
      	<td><bean:write name="employeecontract" property="taskDescription"/></td>
      	<td><bean:write name="employeecontract" property="validFrom"/></td>
      	<td><bean:write name="employeecontract" property="validUntil"/></td>
      	<td align="center"><html:checkbox name="employeecontract" property="freelancer" disabled="true"/> </td>
      	<td align="center"><bean:write name="employeecontract" property="dailyWorkingTime"/></td>
      	<td align="center"><bean:write name="employeecontract" property="vacationEntitlement"/></td>
  
     	 <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditEmployeecontract?ecId=${employeecontract.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Employeecontract" /></html:link> </td>
     		<html:form action="/DeleteEmployeecontract">
     			<td align="center">
     				<html:image onclick="confirmDelete(this.form, ${employeecontract.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Employeecontract"/>
     			</td>
     		</html:form>
     	 </logic:equal>
   	 </tr>
 	</logic:iterate>
 
  <tr>
	 <html:form action="/CreateEmployeecontract">
		<td class="noBborderStyle" colspan="4">
			<html:submit>
				<bean:message key="main.general.button.createemployeecontract.text"/>
			</html:submit>
		</td>
	 </html:form>
  </tr>
</table>
<br><br>
	<table>
		<tr>
			<html:form action="/ShowEmployeecontract?task=back">
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
