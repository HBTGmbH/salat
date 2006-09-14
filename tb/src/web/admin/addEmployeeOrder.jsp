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

<head>
<meta
	http-equiv="Content-Type"
	content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.addemployeeorder.text" /></title>
<link
	rel="stylesheet"
	type="text/css"
	href="/tb/tb.css" />
	
<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreEmployeeorder?task=" + actionVal;
		form.submit();
	}	
			
</script>
	
</head>
<body>

<html:form action="/StoreEmployeeorder">
	<p><h2><bean:message key="main.general.enteremployeeorderproperties.text" />:</h2><br>
	
	<table border="0" cellspacing="0" cellpadding="2" 
			style="background-image:url(/tb/images/backtile.jpg)" class="center">		
		<tr>
            <td align="left" class="noBborderStyle">
               	<b><bean:message key="main.employeeorder.employee.text" /></b>
            </td>
            <td align="left" class="noBborderStyle"> 
            	<html:select property="employeename" 
                	value="<%=(String) request.getSession().getAttribute("currentEmployee")%>" >
					<html:options
						collection="employees"
						labelProperty="name"
						property="name" />
				</html:select>         
				<html:hidden property="employeecontractId" />   
            </td>
        </tr>
			
		<td align="left" class="noBborderStyle">
               		 <b><bean:message key="main.employeeorder.suborder.text" /></b>
           		 </td>
            	<td align="left" class="noBborderStyle">        
             	   <html:select property="suborderId" styleClass="mandatory">
						<html:options
							collection="suborders"
							labelProperty="sign"
							property="id" />
					</html:select>     
					<html:hidden property="suborderId" />   
					<span style="color:red"><html:errors property="suborderId"/></span>       
           	   </td>
			
		<tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.sign.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="sign" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEEORDER_SIGN_MAX_LENGTH %>"/>              
           		 	<span style="color:red"><html:errors property="sign"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.validfrom.text" /></b>
            </td>
            <td align="left" class="noBborderStyle">      
            	
            	<!-- JavaScript Stuff for popup calender -->
    	        <script type="text/javascript" language="JavaScript" src="/tb/CalendarPopup.js"></script>
                <script type="text/javascript" language="JavaScript">
                    document.write(getCalendarStyles());
                </script>
                <script type="text/javascript" language="JavaScript" >
                    function calenderPopup(cal) {
                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
                    }
                    function calenderPopupFrom() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        //cal.select(document.forms[0].validFrom,'from','E yyyy-MM-dd');
                        cal.select(document.forms[0].validFrom,'from','yyyy-MM-dd');
                    }
                    function calenderPopupUntil() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        //cal.select(document.forms[0].validUntil,'until','E yyyy-MM-dd');
                        cal.select(document.forms[0].validUntil,'until','yyyy-MM-dd');
                    }
                </script>
                
		        <html:text property="validFrom"  readonly="false" size="10" maxlength="10" />	
		        <a href="javascript:calenderPopupFrom()" name="from" ID="from" style="text-decoration:none;">
                   	<img src="/tb/images/popupcalendar.gif" width="22" height="22" alt="<bean:message key="main.date.popup.alt.text" />" style="border:0;vertical-align:top">
                </a>
                <span style="color:red"><html:errors property="validFrom"/></span>
            </td>
   		</tr>
   		
   		<tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.validuntil.text" /></b>
            </td>
            <td align="left" class="noBborderStyle">      
            				
		        <html:text property="validUntil"  readonly="false" size="10" maxlength="10" />	
		        <a href="javascript:calenderPopupUntil()" name="until" ID="until" style="text-decoration:none;">
                   	<img src="/tb/images/popupcalendar.gif" width="22" height="22" alt="<bean:message key="main.date.popup.alt.text" />" style="border:0;vertical-align:top">
                </a>
                <span style="color:red"><html:errors property="validUntil"/></span>              
            </td>
   		</tr>
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.standingorder.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:checkbox property="standingorder" />                
           		 	<span style="color:red"><html:errors property="standingorder"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.debithours.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="debithours" size="20"/>                 
           		 	<span style="color:red"><html:errors property="debithours"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.status.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="status" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEEORDER_STATUS_MAX_LENGTH %>"/>                 
           		 	<span style="color:red"><html:errors property="status"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.statusreport.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:checkbox property="statusreport" />                       
           		 	<span style="color:red"><html:errors property="statusreport"/></span>
          	</td>
        </tr>  
        
    </table>
    <br>
    <html:link action="/ShowEmployeeorder"><bean:message key="main.general.showemployeeorders.text" />  </html:link><br>
    <br><br>
    
    <table class="center">
        <tr>
        	<td class="noBborderStyle">        
        		<html:submit onclick="setStoreAction(this.form, 'save')" >
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

