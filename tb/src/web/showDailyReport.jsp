<%@ page import="org.tb.bdom.Employee" %>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%
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
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.mainmenu.daily.text"/></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
    	
 	function setUpdateTimereportsAction(form) {	
 		form.action = "/tb/do/ShowDailyReport?task=refreshTimereports";
		form.submit();
	}	
 
 	function setUpdateOrdersAction(form) {	
 		form.action = "/tb/do/ShowDailyReport?task=refreshOrders";
		form.submit();
	}	
	
	function setUpdateSubordersAction(form, id) {		
		alert('id: ' + id);
 		form.action = "/tb/do/ShowDailyReport?task=refreshSuborders&trId=" + id;
		form.submit();
	}		
	
	function printMyFormElement(form) {		
		alert('element: ' + form.elements['comment'].value);
 		
	}	
	
	function saveTimereportAction(form, id) {		
		alert('test');
 		form.action = "/tb/do/UpdateDailyReport?trId=" + id;
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
		//var agree=confirm("Are you sure you want to delete this entry?");
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteTimereportFromDailyDisplay?trId=" + id;
			form.submit();
		}
	}					
	
	function confirmSave(form, id) {	
		if (form.elements['status'].value == 'closed') {
			var agree=confirm("<bean:message key="main.timereport.confirmclose.text" />");
			if (agree) {
				form.action = "/tb/do/UpdateDailyReport?trId=" + id;
				form.submit();
			}
		}
		else {
			form.action = "/tb/do/UpdateDailyReport?trId=" + id;
			form.submit();
		}
	}					
 
</script>

</head>
<body>

<p><h2><bean:message key="main.general.mainmenu.daily.text"/></h2></p><br><br>

<html:form action="/ShowDailyReport">
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
					<html:hidden property="orderId" />		
				</html:select>           
            </td>

		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.daymonthyear.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="day" 
						value="<%=(String) request.getSession().getAttribute("currentDay")%>" 
						onchange="setUpdateTimereportsAction(this.form)">
					<html:options
						collection="days" 
						property="value" 
						labelProperty="label"
						/>
				</html:select>
			
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
		<!-- 
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.month.text" />:</b></td>
			

		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.year.text" />:</b></td>
			

		</tr>
		 -->
	</table>
<br>
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
			<td align="left"> <b><bean:message key="main.timereport.monthly.save.text"/></b> </td>
			<td align="left"> <b><bean:message key="main.timereport.monthly.delete.text"/></b> </td>	
		</logic:notEqual>
	</tr>
  <logic:iterate id="timereport" name="timereports">
  		<html:form action="/UpdateDailyReport?trId=${timereport.id}">
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
      		<logic:notEqual name="currentEmployee" value="ALL EMPLOYEES" scope="session">
      			<logic:equal name="timereport" property="sortofreport" value="W">   		
      				<td align="left"> 
      						<logic:equal name="employeeAuthorized" value="true" scope="session">
								<html:select property="trOrderId" 
                					value="${timereport.suborder.customerorder.id}">
									<html:options
										collection="orders"
										labelProperty="sign"
										property="id" />
								</html:select>   
							</logic:equal>
							<logic:notEqual name="employeeAuthorized" value="true" scope="session">  
								<logic:equal name="timereport" property="status" value="closed">
									<bean:write name="timereport" property="suborder.customerorder.sign"/>
								</logic:equal>
								<logic:notEqual name="timereport" property="status" value="closed">
									<html:select property="trOrderId" 
                					value="${timereport.suborder.customerorder.id}">
									<html:options
										collection="orders"
										labelProperty="sign"
										property="id" />
									</html:select>   
								</logic:notEqual>						
							</logic:notEqual>				         
         		   </td>
          		  <td align="left"> 
          		  	 <logic:equal name="employeeAuthorized" value="true" scope="session">
						 <html:select property="trSuborderId" styleClass="mandatory"
					 		value="${timereport.suborder.id}">
							<html:options
								collection="suborders"
								labelProperty="sign"
								property="id" />
						</html:select>        
					 </logic:equal>   
					 <logic:notEqual name="employeeAuthorized" value="true" scope="session">  
					 	<logic:equal name="timereport" property="status" value="closed">
							<bean:write name="timereport" property="suborder.sign"/>
						</logic:equal>
						<logic:notEqual name="timereport" property="status" value="closed">   
							<html:select property="trSuborderId" styleClass="mandatory"
					 			value="${timereport.suborder.id}">
								<html:options
									collection="suborders"
									labelProperty="sign"
									property="id" />
							</html:select>  
						</logic:notEqual>  
					</logic:notEqual>                  	
          		  </td>
    		  </logic:equal>
    		  <logic:notEqual name="timereport" property="sortofreport" value="W">
      			  <td></td>
     		 	  <td></td>
      		  </logic:notEqual>
      		  <logic:equal name="employeeAuthorized" value="true" scope="session">
	     		 <td>
	     		 	<html:textarea property="comment" cols="12" rows="1" value="${timereport.taskdescription}"/>
	     		 	<!--  
	     		 	<html:text property="comment" size="10" maxlength="<%="" + org.tb.GlobalConstants.COMMENT_MAX_LENGTH %>" value="${timereport.taskdescription}"/> 
	     		 	-->
	     		 </td>
     		  </logic:equal>
     		  <logic:notEqual name="employeeAuthorized" value="true" scope="session">
     		  	<logic:equal name="timereport" property="status" value="closed">
					<td><bean:write name="timereport" property="taskdescription"/></td>
				</logic:equal>
				<logic:notEqual name="timereport" property="status" value="closed">   
					<td>
						<html:textarea property="comment" cols="12" rows="1" value="${timereport.taskdescription}"/>
						<!--  
						<html:text property="comment" size="10" maxlength="<%="" + org.tb.GlobalConstants.COMMENT_MAX_LENGTH %>" value="${timereport.taskdescription}"/> 
						-->
					</td>
				</logic:notEqual>
     		  </logic:notEqual>
     		  
     		  <logic:equal name="timereport" property="sortofreport" value="W">
     		   <td align=center nowrap>
     			 <logic:equal name="employeeAuthorized" value="true" scope="session">
     			 	<logic:equal name="timereport" property="sortofreport" value="W">
     		 			<html:select name="timereport" property="selectedHourBegin" 
                			value="${timereport.beginhour}">				 	
							<html:options
								collection="hours" 
								property="value" 
								labelProperty="label"
							/>
						</html:select> <html:select property="selectedMinuteBegin" value="${timereport.beginminute}">
							<html:options
								collection="minutes" 
								property="value" 
								labelProperty="label"
							/>
						</html:select>	
					</logic:equal>
					<logic:notEqual name="timereport" property="sortofreport" value="W">
						<bean:write name="timereport" property="beginhour" format="#00"/><bean:write name="timereport" property="beginminute" format="#00"/>
					</logic:notEqual>
				</logic:equal>
				<logic:notEqual name="employeeAuthorized" value="true" scope="session">  
					 <logic:equal name="timereport" property="status" value="closed">
						<bean:write name="timereport" property="beginhour" format="#00"/><bean:write name="timereport" property="beginminute" format="#00"/>
					 </logic:equal>
					 <logic:notEqual name="timereport" property="status" value="closed">
					 	<logic:equal name="timereport" property="sortofreport" value="W">   
					 		<html:select name="timereport" property="selectedHourBegin" 
                			value="${timereport.beginhour}">				 	
							<html:options
								collection="hours" 
								property="value" 
								labelProperty="label"
							/>
							</html:select> <html:select property="selectedMinuteBegin" value="${timereport.beginminute}">
							<html:options
								collection="minutes" 
								property="value" 
								labelProperty="label"
							/>
							</html:select>	
						</logic:equal>
						<logic:notEqual name="timereport" property="sortofreport" value="W">
							<bean:write name="timereport" property="beginhour" format="#00"/><bean:write name="timereport" property="beginminute" format="#00"/>
						</logic:notEqual>
					 </logic:notEqual>
				</logic:notEqual>
     		 </td>
     		 <td align=center nowrap>
      			 <logic:equal name="employeeAuthorized" value="true" scope="session">
      			 	<logic:equal name="timereport" property="sortofreport" value="W">   
     		 			<html:select name="timereport" property="selectedHourEnd" 
                			value="${timereport.endhour}">				 	
							<html:options
								collection="hours" 
								property="value" 
								labelProperty="label"
							/>
						</html:select> <html:select property="selectedMinuteEnd" value="${timereport.endminute}">
							<html:options
								collection="minutes" 
								property="value" 
								labelProperty="label"
							/>
						</html:select>	
					</logic:equal>
					<logic:notEqual name="timereport" property="sortofreport" value="W">
						<bean:write name="timereport" property="endhour" format="#00"/><bean:write name="timereport" property="endminute" format="#00"/>
					</logic:notEqual>
				</logic:equal>
				<logic:notEqual name="employeeAuthorized" value="true" scope="session">  
					 <logic:equal name="timereport" property="status" value="closed">
						<bean:write name="timereport" property="endhour" format="#00"/><bean:write name="timereport" property="endminute" format="#00"/>
					 </logic:equal>
					 <logic:notEqual name="timereport" property="status" value="closed">  
					 	<logic:equal name="timereport" property="sortofreport" value="W">    
						 	<html:select name="timereport" property="selectedHourEnd" 
                			value="${timereport.endhour}">				 	
							<html:options
								collection="hours" 
								property="value" 
								labelProperty="label"
							/>
						</html:select> <html:select property="selectedMinuteEnd" value="${timereport.endminute}">
							<html:options
								collection="minutes" 
								property="value" 
								labelProperty="label"
							/>
						</html:select>	
					 </logic:equal>
					 <logic:notEqual name="timereport" property="sortofreport" value="W">
						<bean:write name="timereport" property="endhour" format="#00"/><bean:write name="timereport" property="endminute" format="#00"/>
					</logic:notEqual>
					 </logic:notEqual>
				</logic:notEqual>
   		   </td>
     	   <td align=center><bean:write name="timereport" property="hours" format="#0.00"/></td>
	      
		      <td>
		      	<logic:equal name="employeeAuthorized" value="true" scope="session">
	      			<html:text property="costs" size="8" value="${timereport.costs}"/>    
	      		</logic:equal>
				<logic:notEqual name="employeeAuthorized" value="true" scope="session">  
					 <logic:equal name="timereport" property="status" value="closed">
					 	<bean:write name="timereport" property="costs"/>
					 </logic:equal>
					 <logic:notEqual name="timereport" property="status" value="closed">
					 	<html:text property="costs" size="8" value="${timereport.costs}"/>    
					 </logic:notEqual>
				</logic:notEqual>   
	    	  </td>
    	  	  <td>
    	 	 	<logic:equal name="employeeAuthorized" value="true" scope="session">
    	 	 		<!-- This is an ugly solution! Should be improved as soon as we have more options... -->
	      			<logic:equal name="timereport" property="status" value="closed">
	      				<html:select property="status" value="${timereport.status}">
							<html:option value="closed"><bean:message key="main.timereport.select.status.closed.text"/></html:option>
							<html:option value="open"><bean:message key="main.timereport.select.status.open.text"/></html:option>
						</html:select> 
					</logic:equal>
					<logic:notEqual name="timereport" property="status" value="closed">
	      				<html:select property="status" value="${timereport.status}">
							<html:option value="open"><bean:message key="main.timereport.select.status.open.text"/></html:option>
							<html:option value="closed"><bean:message key="main.timereport.select.status.closed.text"/></html:option>							
						</html:select> 
					</logic:notEqual>
	      			<!--  
	      			<html:text property="status" size="10" maxlength="<%="" + org.tb.GlobalConstants.STATUS_MAX_LENGTH %>" value="${timereport.status}"/>    
	      			-->
	      		</logic:equal>
				<logic:notEqual name="employeeAuthorized" value="true" scope="session">  				
					 <logic:equal name="timereport" property="status" value="closed">
    		  			<bean:message key="main.timereport.select.status.closed.text"/>
    				 </logic:equal>
    				 <logic:equal name="timereport" property="status" value="open">
    				  	<html:select property="status" value="${timereport.status}">
							<html:option value="open"><bean:message key="main.timereport.select.status.open.text"/></html:option>
							<html:option value="closed"><bean:message key="main.timereport.select.status.closed.text"/></html:option>							
						</html:select> 
    		 		 </logic:equal>					 
				</logic:notEqual>   
    	 	 </td>
      	 </logic:equal>
     	 <logic:notEqual name="timereport" property="sortofreport" value="W">
      		<td></td>
     		 <td></td>
     		  <td></td>
     		   <td></td>
     		    <td></td>
     	 </logic:notEqual>
    	  <logic:equal name="employeeAuthorized" value="true" scope="session">
      		<td align="center">
      			<html:image onclick="confirmSave(this.form, ${timereport.id})" src="/tb/images/Save.gif" alt="Save Timereport"/>
      		 </td>
      		<td align="center"> 
      			<html:image onclick="confirmDelete(this.form, ${timereport.id})" 
      				src="/tb/images/Delete.gif" alt="Delete Timereport"/>
      			<!--  
      			<html:link href="/tb/do/DeleteTimereportFromDailyDisplay?trId=${timereport.id}">
      				<img src="/tb/images/Delete.gif" alt="Delete Timereport" />
      			</html:link> 
      			-->
      		</td>
     	 </logic:equal>
     	 <logic:notEqual name="employeeAuthorized" value="true" scope="session">
      		<logic:equal name="timereport" property="status" value="closed">
      			<td align="center"></td>
     			<td align="center"></td>
      		</logic:equal>
      		<logic:notEqual name="timereport" property="status" value="closed">
      			<td align="center"> 
      				<html:image onclick="confirmSave(this.form, ${timereport.id})" src="/tb/images/Save.gif" alt="Save Timereport"/>
      			</td>
     			 <td align="center"> 
     			 	<html:image onclick="confirmDelete(this.form, ${timereport.id})" 
      					src="/tb/images/Delete.gif" alt="Delete Timereport"/>
     			 	<!-- 
     			 	<html:link href="/tb/do/DeleteTimereportFromDailyDisplay?trId=${timereport.id}">
     			 		<img src="/tb/images/Delete.gif" alt="Delete Timereport" />
     			 	</html:link> 
     			 	-->
     			 </td>
      		</logic:notEqual>
     	 </logic:notEqual>
     	</logic:notEqual>
     	
     	<logic:equal name="currentEmployee" value="ALL EMPLOYEES" scope="session">
     		<logic:equal name="timereport" property="sortofreport" value="W">
     			<td align=center><bean:write name="timereport" property="suborder.customerorder.sign"/></td>
     			<td align=center><bean:write name="timereport" property="suborder.sign"/></td>
     		</logic:equal>
     		<logic:notEqual name="timereport" property="sortofreport" value="W">
      			<td></td>
     		 	<td></td>
      		</logic:notEqual>
     		<td><bean:write name="timereport" property="taskdescription"/></td>
    	    <td align=center>
      			<bean:write name="timereport" property="beginhour" format="#00"/><bean:write name="timereport" property="beginminute" format="#00"/>    
      		</td>
      		<td align=center>
      			<bean:write name="timereport" property="endhour" format="#00"/><bean:write name="timereport" property="endminute" format="#00"/>    
      		</td>
      		<td align=center><bean:write name="timereport" property="hours" format="#0.00"/></td>
      		<logic:equal name="timereport" property="sortofreport" value="W">
	      		<td><bean:write name="timereport" property="costs" format="#0.00"/></td>
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
      		</logic:notEqual>
     	</logic:equal>
    </tr>
    </html:form>
  </logic:iterate>
  <tr>	 
  	<html:form action="/CreateDailyReport">	
	 		<td class="noBborderStyle" colspan="6">    		    
        		<html:submit>
        			<bean:message key="main.general.button.createnewreport.text"/>
        		</html:submit>  
        	</td>    		
	 	</html:form>
  </tr>
</table>
<br>

<span style="color:red">
<b>
<html:errors property="trSuborderId"/><br>
<html:errors property="selectedHourEnd"/><br>
<html:errors property="costs"/><br>
<html:errors property="status"/><br>
<html:errors property="comment"/>
</b>
</span> 
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
<html:link action="/ShowMonthlyReport"><bean:message key="main.general.showmonthly.text" /> </html:link>
<br><br>
	<table>
		<tr>			
			<html:form action="/ShowDailyReport?task=back">
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
