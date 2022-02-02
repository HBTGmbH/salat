<%@ page import="org.tb.bdom.Employee" %>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%
	Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 
	if ((loginEmployee.getStatus().equalsIgnoreCase("bl")) || 
		(loginEmployee.getStatus().equalsIgnoreCase("pl"))) {
		request.getSession().setAttribute("employeeAuthorized", "true");
	}
	
	Double hourBalance = (Double) request.getSession().getAttribute("hourbalance");
	int displayLength = 0;
	for (int i=0; i<hourBalance.toString().length(); i++) {
		if (hourBalance.toString().charAt(i) == '.') {
			displayLength = Math.min(i+3, hourBalance.toString().length());
			break;
		}
	}
	String hourBalanceDisplay = hourBalance.toString().substring(0,displayLength);
	
	String vacation = (String) request.getSession().getAttribute("vacation");
	
%>

<html:html>
<head>
<html:base />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.mainmenu.monthly.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/style/tb.css" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />

<script type="text/javascript" language="JavaScript">
    	
 	function setUpdateTimereportsAction(form) {	
 		form.action = "/tb/do/ShowMonthlyReport?task=refreshTimereports";
		form.submit();
	}	
	
	function createNewReportAction(form) {	
 		form.action = "/tb/do/CreateDailyReport";
		form.submit();
	}	
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreDailyReport?task=" + actionVal;
		form.submit();
	}
	
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteTimereportFromMonthlyDisplay?trId=" + id;
			form.submit();
		}
	}								
 
</script>

</head>
<body>
<html:errors />
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<p>
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.monthly.text" /><br></span>
</p>
<br>
<html:form action="/ShowMonthlyReport">
	<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.employee.fullname.text" />:</b></td>
			<td align="left" class="noBborderStyle"> 
                <html:select property="employeename" 
                	value="<%=(String) request.getSession().getAttribute("currentEmployee")%>" 
                	onchange="setUpdateTimereportsAction(this.form)">                	
					<logic:equal name="employeeAuthorized" value="true" scope="session">
                		<html:option value="ALL EMPLOYEES"><bean:message key="main.general.allemployees.text"/></html:option>
                	</logic:equal>
					<html:options
						collection="employees"
						labelProperty="name"
						property="name" />				
				</html:select>  
				<logic:equal name="currentEmployee" value="ALL EMPLOYEES" scope="session">
					<span style="color:red">
						<b><bean:message key="main.general.selectemployee.editable.text"/>.</b>
					</span>    
				</logic:equal>         
            </td>

		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.customerorder.text" />:</b></td>
			<td align="left" class="noBborderStyle"> 
                <html:select property="order" 
	                value="<%=(String) request.getSession().getAttribute("currentOrder")%>" 
                	onchange="setUpdateTimereportsAction(this.form)">           	
                	<html:option value="ALL ORDERS"><bean:message key="main.general.allorders.text"/></html:option>
					<html:options
						collection="orders"
						labelProperty="sign"
						property="sign" />			
				</html:select>    
				<html:hidden property="orderId" />       
            </td>

		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.monthyear.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="month" 
						value="<%=(String) request.getSession().getAttribute("currentMonth")%>" 
						onchange="setUpdateTimereportsAction(this.form)">
					<html:option value="Jan"><bean:message key="main.timereport.select.month.jan.text"/></html:option>
					<html:option value="Feb"><bean:message key="main.timereport.select.month.feb.text"/></html:option>
					<html:option value="Mar"><bean:message key="main.timereport.select.month.mar.text"/></html:option>
					<html:option value="Apr"><bean:message key="main.timereport.select.month.apr.text"/></html:option>
					<html:option value="May"><bean:message key="main.timereport.select.month.may.text"/></html:option>
					<html:option value="Jun"><bean:message key="main.timereport.select.month.jun.text"/></html:option>
					<html:option value="Jul"><bean:message key="main.timereport.select.month.jul.text"/></html:option>
					<html:option value="Aug"><bean:message key="main.timereport.select.month.aug.text"/></html:option>
					<html:option value="Sep"><bean:message key="main.timereport.select.month.sep.text"/></html:option>
					<html:option value="Oct"><bean:message key="main.timereport.select.month.oct.text"/></html:option>
					<html:option value="Nov"><bean:message key="main.timereport.select.month.nov.text"/></html:option>
					<html:option value="Dec"><bean:message key="main.timereport.select.month.dec.text"/></html:option>
				</html:select>
			
                <html:select property="year" 
                	value="<%=(String) request.getSession().getAttribute("currentYear")%>" 
                	onchange="setUpdateTimereportsAction(this.form)">
					<html:options
						collection="years" 
						property="value" 
						labelProperty="label"
						/>
				</html:select>         
            </td>

		</tr>
	</table>
<br>
<html:hidden property="orderId" />
</html:form>

<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   	<tr>
   		<logic:equal name="currentEmployee" value="ALL EMPLOYEES" scope="session">
   			<td align="left"> <b><bean:message key="main.timereport.monthly.employee.text"/></b> </td>
   		</logic:equal>	
		<td align="left"> <b><bean:message key="main.timereport.monthly.refday.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.timereport.monthly.sortofreport.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.timereport.monthly.customerorder.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.timereport.monthly.suborder.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.timereport.monthly.taskdescription.text"/></b> </td>
		<td align="center"> <b><bean:message key="main.timereport.monthly.begin.text"/></b> </td>
		<td align="center"> <b><bean:message key="main.timereport.monthly.end.text"/></b> </td>
		<td align="center"> <b><bean:message key="main.timereport.monthly.hours.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.timereport.monthly.costs.text"/></b> </td>
		<td align="left"> <b><bean:message key="main.timereport.monthly.status.text"/></b> </td>	
		<logic:notEqual name="currentEmployee" value="ALL EMPLOYEES" scope="session">
			<td align="left"> <b><bean:message key="main.timereport.monthly.edit.text"/></b> </td>
			<td align="left"> <b><bean:message key="main.timereport.monthly.delete.text"/></b> </td>	
		</logic:notEqual>
	</tr>
  <logic:iterate id="timereport" name="timereports">
    <tr>
      <logic:equal name="currentEmployee" value="ALL EMPLOYEES" scope="session">
      	<td><bean:write name="timereport" property="employeecontract.employee.name"/></td>
      </logic:equal>
      <td title='<bean:write name="timereport" property="referenceday.name"/>'>
      	<logic:equal name="timereport" property="referenceday.holiday" value="true">
      		<span style="color:red"> 
      			<bean:write name="timereport" property="referenceday.dow"/>
      			<bean:write name="timereport" property="referenceday.refdate"/>
      		</span>
      	</logic:equal>
      	<logic:equal name="timereport" property="referenceday.holiday" value="false">
      		<bean:write name="timereport" property="referenceday.dow"/>
      		<bean:write name="timereport" property="referenceday.refdate"/>
      	</logic:equal>
      </td>
      
      <td align=center>
      	<logic:equal name="timereport" property="sortofreport" value="W">
      		<bean:message key="main.timereport.monthly.sortofreport.work.text"/>
      	</logic:equal>
      	<logic:equal name="timereport" property="sortofreport" value="V">
      		<bean:message key="main.timereport.monthly.sortofreport.vacation.text"/>
      	</logic:equal>
      	<logic:equal name="timereport" property="sortofreport" value="S">
      		<bean:message key="main.timereport.monthly.sortofreport.sick.text"/>
      	</logic:equal>
      </td>
          
      <logic:equal name="timereport" property="sortofreport" value="W">
	      <td><bean:write name="timereport" property="suborder.customerorder.sign"/></td>
    	  <td><bean:write name="timereport" property="suborder.sign"/></td>
      </logic:equal>
      <logic:notEqual name="timereport" property="sortofreport" value="W">
      		<td></td>
     		 <td></td>
      </logic:notEqual>
      <td><bean:write name="timereport" property="taskdescription"/></td>
      <logic:equal name="timereport" property="sortofreport" value="W">
    	  <td align=center>
    	  	<bean:write name="timereport" property="beginhour" format="#00"/><bean:write name="timereport" property="beginminute" format="#00"/>    
    	  </td>
    	  <td align=center>
    	  	<bean:write name="timereport" property="endhour" format="#00"/><bean:write name="timereport" property="endminute" format="#00"/>    
    	  </td>
    	  <td align=center><bean:write name="timereport" property="hours" format="#0.00"/></td>
      
	      <td><bean:write name="timereport" property="costs" format="#0.00"/></td>
    	  <!-- 
    	  <td><bean:write name="timereport" property="status"/></td>
    	   -->
    	   <td>
    		  <logic:equal name="timereport" property="status" value="closed">
    		  	<bean:message key="main.timereport.select.status.closed.text"/>
    		  </logic:equal>
    		  <logic:equal name="timereport" property="status" value="open">
    		  	<bean:message key="main.timereport.select.status.open.text"/>
    		  </logic:equal>
    	  </td>
      </logic:equal>
      <logic:notEqual name="timereport" property="sortofreport" value="W">
      		<td></td>
     		 <td></td>
     		 <td></td>
     		 <td></td>
     		 <td></td>
      </logic:notEqual>
      <logic:notEqual name="currentEmployee" value="ALL EMPLOYEES" scope="session">
    	  <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center"> <html:link href="/tb/do/EditDailyReport?trId=${timereport.id}">
      			<img src="/tb/images/Edit.gif" alt="Edit Timereport" /></html:link> </td>
      		<html:form action="/DeleteTimereportFromMonthlyDisplay">
      			<td align="center">      			
      				<html:image onclick="confirmDelete(this.form, ${timereport.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Timereport"/>     			
      			</td>
      		</html:form>
     	 </logic:equal>
     	 <logic:notEqual name="employeeAuthorized" value="true" scope="session">
      		<logic:equal name="timereport" property="status" value="closed">
      			<td align="center"></td>
     			<td align="center"></td>
      		</logic:equal>
      		<logic:notEqual name="timereport" property="status" value="closed">
      			<td align="center"> <html:link href="/tb/do/EditDailyReport?trId=${timereport.id}">
      				<img src="/tb/images/Edit.gif" alt="Edit Timereport" /></html:link> </td>
     			<html:form action="/DeleteTimereportFromMonthlyDisplay">
     				<td align="center">  	
     			 		<html:image onclick="confirmDelete(this.form, ${timereport.id})" 
      						src="/tb/images/Delete.gif" alt="Delete Timereport"/>	
     				</td>
     			</html:form>
      		</logic:notEqual>
     	 </logic:notEqual>
      </logic:notEqual>
    </tr>
  </logic:iterate>
  <tr>  	
  		<html:form action="/CreateDailyReport">		     
        	<td class="noBborderStyle" colspan="6">   
        		<html:submit styleId="button">
        			<bean:message key="main.general.button.createnewreport.text"/>
        		</html:submit>   
        	</td>  		
	 	</html:form> 
  </tr>
</table>
<br>

<logic:notEqual name="currentEmployee" value="ALL EMPLOYEES" scope="session">
	<table border="0" cellspacing="0" cellpadding="2"
		style="background-image:url(/tb/images/backtile.jpg)" class="center">
   		<tr>
			<td>
				<b><bean:message key="main.general.button.monthlyhourbalance.text"/>: </b>
			</td>
			<td>
				<logic:greaterEqual name="hourbalance" value="0.0" scope="session">
  	  				<b><%=hourBalanceDisplay%></b>
  				</logic:greaterEqual>
  				<logic:lessThan name="hourbalance" value="0.0" scope="session">
  	  				<b><span style="color:red"> <%=hourBalanceDisplay%> </span> </b>
  				</logic:lessThan>
			</td>
		</tr>
		<tr>
			<td>
    			<b><bean:message key="main.general.button.vacationused.text"/>: </b>
			</td>
			<td>
				<b><%=vacation%></b>
			</td>
		</tr>
	</table>
</logic:notEqual>

<br>
<html:link action="/ShowDailyReport"><bean:message key="main.general.showdaily.text" /> </html:link>
<br><br>
	<table>
		<tr>
			<html:form action="/ShowMonthlyReport?task=back">
			<td class="noBborderStyle">        
        		<html:submit>
        			<bean:message key="main.general.button.backmainmenu.text"/>
        		</html:submit>
        	</td>
			</html:form>     
		</tr>

	</table>

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
