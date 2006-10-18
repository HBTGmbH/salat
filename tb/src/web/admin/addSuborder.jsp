<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@taglib
	uri="http://struts.apache.org/tags-html"
	prefix="html"%>
	<%@taglib
	uri="http://struts.apache.org/tags-html-el"
	prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<%
	Long coId = (Long) request.getSession().getAttribute("currentOrderId");
	String coIdString = coId.toString();
	String invoiceString = (String) request.getSession().getAttribute("invoice");
	Double hr = (Double) request.getSession().getAttribute("hourlyRate");
	String hrString = hr.toString();
	String currency = (String) request.getSession().getAttribute("currency");
%>


<head>
<meta
	http-equiv="Content-Type"
	content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.addsuborder.text" /></title>
<link
	rel="stylesheet"
	type="text/css"
	href="/tb/tb.css" />
	
<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreSuborder?task=" + actionVal;
		form.submit();
	}	
			
</script>
	
</head>
<body>

<html:form action="/StoreSuborder">
	<p><h2><bean:message key="main.general.entersuborderproperties.text" />:</h2><br>
	
	<table border="0" cellspacing="0" cellpadding="2" 
			style="background-image:url(/tb/images/backtile.jpg)" class="center">		
		<tr>
            <td align="left" class="noBborderStyle">
               	<b><bean:message key="main.suborder.customerorder.text" /></b>
            </td>
            <td align="left" class="noBborderStyle"> 
                	<html:select property="customerorderId"
                		value="<%=coIdString%>"
                		onchange="setStoreAction(this.form,'refreshHourlyRate')">
						<html:options
							collection="customerorders"
							labelProperty="sign"
							property="id" />
					</html:select>            
            </td>
        </tr>
			
		<tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.suborder.sign.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="sign" size="40" maxlength="<%="" + org.tb.GlobalConstants.SUBORDER_SIGN_MAX_LENGTH %>"/>              
           		 	<span style="color:red"><html:errors property="sign"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.suborder.description.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:textarea property="description" cols="30" rows="4"/>                
           		 	<span style="color:red"><html:errors property="description"/></span>
          	</td>
        </tr>  
        
        <tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.invoice.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="invoice" value="<%=invoiceString%>" >
					<html:option value="Y"><bean:message key="main.general.suborder.invoice.yes" /></html:option>
					<html:option value="N"><bean:message key="main.general.suborder.invoice.no" /></html:option>
					<html:option value="U"><bean:message key="main.general.suborder.invoice.undefined" /></html:option>
				</html:select>
				<span style="color:red"><html:errors property="invoice"/></span>
			</td>

		</tr>
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.suborder.currency.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="currency" size="10" value="<%=currency%>"/>                 
           		 	<span style="color:red"><html:errors property="currency"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.suborder.hourlyrate.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="hourlyRate" size="10" value="<%=hrString%>" />                 
           		 	<span style="color:red"><html:errors property="hourlyRate"/></span>
          	</td>
        </tr>  
        
    </table>
    <br>
    <html:link action="/ShowSuborder"><bean:message key="main.general.showsuborders.text" />  </html:link><br>
    <br><br>
    
    <table class="center">
        <tr>
        	<td class="noBborderStyle">        
        		<html:submit onclick="setStoreAction(this.form, 'save');return false" >
        			<bean:message key="main.general.button.save.text"/>
        		</html:submit>
        	</td>
        	<td class="noBborderStyle">        
        		<html:submit onclick="setStoreAction(this.form, 'reset')" >
        			<bean:message key="main.general.button.reset.text"/>
        		</html:submit>
        	</td>
        	<td class="noBborderStyle">        
        		<html:submit onclick="setStoreAction(this.form, 'back')">
        			<bean:message key="main.general.button.backmainmenu.text"/>
        		</html:submit>
        	</td>
        </tr>  
	</table>
 <html:hidden property="id" />
</html:form>
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

