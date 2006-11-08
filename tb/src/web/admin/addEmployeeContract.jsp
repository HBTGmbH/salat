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
<title><bean:message key="main.general.addemployeecontract.text" /></title>
<link
	rel="stylesheet"
	type="text/css"
	href="/tb/tb.css" />
	
<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreEmployeecontract?task=" + actionVal;
		form.submit();
	}
		
	function afterCalenderClick() {
	}
		
</script>
	
</head>
<body>

<html:form action="/StoreEmployeecontract">
	<p><h2><bean:message key="main.general.enteremployeecontractproperties.text" />:</h2><br>
	
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">		
		<tr>
            <td align="left" class="noBborderStyle">
               	<b><bean:message key="main.employeecontract.employee.text" /></b>
            </td>
            <td align="left" class="noBborderStyle"> 
            	<html:select property="employeename" 
                	value="<%=(String) request.getSession().getAttribute("currentEmployee")%>" >
					<html:options
						collection="employees"
						labelProperty="name"
						property="name" />
				</html:select>         
				<html:hidden property="employeeId" /> 
				<span style="color:red"><html:errors property="employeename"/></span>  
            </td>
        </tr>
			
		<tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeecontract.taskdescription.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:textarea property="taskdescription" cols="40" rows="6"/>                
           		 	<span style="color:red"><html:errors property="taskdescription"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeecontract.validfrom.text" /></b>
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
                <b><bean:message key="main.employeecontract.validuntil.text" /></b>
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
                <b><bean:message key="main.employeecontract.freelancer.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:checkbox property="freelancer" />                
           		 	<span style="color:red"><html:errors property="freelancer"/></span>
          	</td>
        </tr>  
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeecontract.dailyworkingtime.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="dailyworkingtime" size="10"/>                 
           		 	<span style="color:red"><html:errors property="dailyworkingtime"/></span>
          	</td>
        </tr>
        
        <tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeecontract.yearlyvacation.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="yearlyvacation" size="10"/>                 
           		 	<span style="color:red"><html:errors property="yearlyvacation"/></span>
          	</td>
        </tr>    
        
    </table>
    <br>
    <html:link action="/ShowEmployeecontract"><bean:message key="main.general.showemployeecontracts.text" /> </html:link><br>
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

