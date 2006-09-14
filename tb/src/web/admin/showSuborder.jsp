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
<title><bean:message key="main.general.mainmenu.suborders.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteSuborder?soId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.suborders.text"/></h2></p><br><br>

<span style="color:red"><html:errors /><br></span>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
		<td align="left"> <b><bean:message key="main.suborder.customerorder.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.suborder.sign.text"/></b> </td>		
		<td align="left"> <b><bean:message key="main.suborder.description.text"/></b> </td>		
		<td align="left"> <b><bean:message key="main.suborder.invoice.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.suborder.currency.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.suborder.hourlyrate.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.suborder.edit.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.suborder.delete.text"/></b> </td>	
	</tr>
  <logic:iterate id="suborder" name="suborders">
    <tr>
      	<td><bean:write name="suborder" property="customerorder.sign"/></td>
      	<td><bean:write name="suborder" property="sign"/></td>
      	<td><bean:write name="suborder" property="description"/></td> 
      	<td align="center">
      		<logic:equal name="suborder" property="invoice" value="Y">
      			<bean:message key="main.suborder.invoice.yes.text"/>
      		</logic:equal>
      		<logic:equal name="suborder" property="invoice" value="N">
      			<bean:message key="main.suborder.invoice.no.text"/>
      		</logic:equal>
      		<logic:equal name="suborder" property="invoice" value="U">
      			<bean:message key="main.suborder.invoice.undefined.text"/>
      		</logic:equal>
      	</td>
      	<td><bean:write name="suborder" property="currency"/></td>
      	<td><bean:write name="suborder" property="hourly_rate"/></td>
    
     	 <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditSuborder?soId=${suborder.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Suborder" /></html:link> </td>
     		<html:form action="/DeleteSuborder">
	     		<td align="center"> 
    	 			<html:image onclick="confirmDelete(this.form, ${suborder.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Suborder"/>
     			</td>
     		</html:form>
     	 </logic:equal>
    </tr>
  </logic:iterate>
  <tr>
	 <html:form action="/CreateSuborder">
		<td class="noBborderStyle" colspan="4">
			<html:submit>
				<bean:message key="main.general.button.createsuborder.text"/>
			</html:submit>
		</td>
	 </html:form>
  </tr>
</table>
<br><br>
	<table>
		<tr>
			<html:form action="/ShowSuborder?task=back">
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
