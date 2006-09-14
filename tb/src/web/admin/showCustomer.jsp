
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
<title><bean:message key="main.general.mainmenu.customers.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteCustomer?cuId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.customers.text"/></h2></p><br><br>

<span style="color:red"><html:errors /><br></span>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
		<td align="left"> <b><bean:message key="main.customer.name.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.customer.address.text"/></b> </td>		
		<td align="left"> <b><bean:message key="main.customer.edit.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.customer.delete.text"/></b> </td>	
	</tr>
  <logic:iterate id="customer" name="customers">
    <tr>
      	<td><bean:write name="customer" property="name"/></td>
      	<td><bean:write name="customer" property="address"/></td>
    
     	 <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditCustomer?cuId=${customer.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Customer" /></html:link> </td>
     		<html:form action="/DeleteCustomer">
	     		<td align="center"> 
    	 			<html:image onclick="confirmDelete(this.form, ${customer.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Customer"/>
     			</td>
     		</html:form>
     	 </logic:equal>
    </tr>
  </logic:iterate>
  <tr>
	 <html:form action="/CreateCustomer">
		<td class="noBborderStyle" colspan="2">
			<html:submit>
				<bean:message key="main.general.button.createcustomer.text"/>
			</html:submit>
		</td>
	 </html:form>
  </tr>
</table>
<br><br>
	<table>
		<tr>			
			<html:form action="/ShowCustomer?task=back">
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
