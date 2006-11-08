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
	String genderString = (String) request.getSession().getAttribute("gender");	
	String statusString = (String) request.getSession().getAttribute("employeeStatus");
%>

<head>
<meta
	http-equiv="Content-Type"
	content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.addemployee.text" /></title>
<link
	rel="stylesheet"
	type="text/css"
	href="/tb/tb.css" />
	
<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreEmployee?task=" + actionVal;
		form.submit();
	}	
			
</script>
	
</head>
<body>

<html:form action="/StoreEmployee">
	<p><h2><bean:message key="main.general.enteremployeeproperties.text" />:</h2><br>
	
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">
		<tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.firstname.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="firstname" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_FIRSTNAME_MAX_LENGTH %>"/>              
           		 	<span style="color:red"><html:errors property="firstname"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.lastname.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		    <html:text property="lastname" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_LASTNAME_MAX_LENGTH %>"/>         
           		 	<span style="color:red"><html:errors property="lastname"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.sign.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		    <html:text property="sign" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_SIGN_MAX_LENGTH %>"/>         
           		 	<span style="color:red"><html:errors property="sign"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.loginname.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		    <html:text property="loginname" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_LOGINNAME_MAX_LENGTH %>"/>         
           		 	<span style="color:red"><html:errors property="loginname"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.password.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		    <html:text property="password" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH %>"/>         
           		 	<span style="color:red"><html:errors property="password"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.status.text" /></b>
            </td>
            <td align="left" class="noBborderStyle">
				<html:select property="status" value="<%=statusString%>" >
					<html:option value="ma"><bean:message key="main.employee.status.ma" /></html:option>
					<html:option value="pl"><bean:message key="main.employee.status.pl" /></html:option>
					<html:option value="bl"><bean:message key="main.employee.status.bl" /></html:option>
				</html:select>
			</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employee.gender.text" /></b>
            </td>
            <td align="left" class="noBborderStyle">
				<html:select property="gender" value="<%=genderString%>" >
					<html:option value="m"><bean:message key="main.general.employee.gender.male" /></html:option>
					<html:option value="f"><bean:message key="main.general.employee.gender.female" /></html:option>
				</html:select>
			</td>
        </tr>  
    </table>
    <br>
    <html:link action="/ShowEmployee"><bean:message key="main.general.showemployees.text" />  </html:link><br>
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

