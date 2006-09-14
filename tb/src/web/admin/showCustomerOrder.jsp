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
<title><bean:message key="main.general.mainmenu.customerorders.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteCustomerorder?coId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.customerorders.text"/></h2></p><br><br>

<span style="color:red"><html:errors /><br></span>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
		<td align="left"> <b><bean:message key="main.customerorder.customer.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.customerorder.sign.text"/></b> </td>		
		<td align="left"> <b><bean:message key="main.customerorder.description.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.validfrom.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.validuntil.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.responsiblecustomer.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.responsiblehbt.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.ordercustomer.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.currency.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.hourlyrate.text"/></b> </td>	
		<td align="left"> <b><bean:message key="main.customerorder.edit.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.customerorder.delete.text"/></b> </td>	
	</tr>
  <logic:iterate id="customerorder" name="customerorders">
    <tr>
      	<td><bean:write name="customerorder" property="customer.name"/></td>
      	<td><bean:write name="customerorder" property="sign"/></td>
      	<td><bean:write name="customerorder" property="description"/></td>
      	<td><bean:write name="customerorder" property="fromDate"/></td>
      	<td><bean:write name="customerorder" property="untilDate"/></td>
      	<td><bean:write name="customerorder" property="responsible_customer"/></td>
      	<td><bean:write name="customerorder" property="responsible_hbt"/></td>
      	<td><bean:write name="customerorder" property="order_customer"/></td>
      	<td><bean:write name="customerorder" property="currency"/></td>
      	<td><bean:write name="customerorder" property="hourly_rate"/></td>
    
     	 <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditCustomerorder?coId=${customerorder.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Customerorder" /></html:link> </td>
     		<html:form action="/DeleteCustomerorder">
     			<td align="center">
     				<html:image onclick="confirmDelete(this.form, ${customerorder.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Customerorder"/>
     			</td>
     		</html:form>
     	 </logic:equal>
    </tr>
  </logic:iterate>
  <tr>
	 <html:form action="/CreateCustomerorder">
		<td class="noBborderStyle" colspan="4">
			<html:submit>
				<bean:message key="main.general.button.createcustomerorder.text"/>
			</html:submit>
		</td>
	 </html:form>
  </tr>
</table>
<br><br>
	<table>
		<tr>
			<html:form action="/ShowCustomerorder?task=back">
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
