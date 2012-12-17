<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addtimereport.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<% java.text.DateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd");%>
<script type="text/javascript" language="JavaScript">
 	var req;
 		
 	function setUpdateOrdersAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=refreshOrders";
		form.submit();
	}	
	
	function setUpdateSubordersAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=refreshSuborders";
		form.submit();
	}			
	
	function adjustBeginTimeAction(form) {
		form.action = "/tb/do/StoreDailyReport?task=adjustBeginTime";
		form.submit();
	}
	
	function adjustSuborderSignChangedAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=adjustSuborderSignChanged";
		form.submit();
	}		
	
	function adjustSuborderDescriptionChangedAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=adjustSuborderDescriptionChanged";
		form.submit();
	}
	
	function afterCalenderClick() {
		document.forms[0].action = "/tb/do/StoreDailyReport?task=adjustBeginTime";
		document.forms[0].submit();	
	}			
	
	function setDate(howMuch) {
		document.forms[0].action = "/tb/do/StoreDailyReport?task=setDate&howMuch=" + howMuch;
		document.forms[0].submit();
	}
	
	function setUpdateSortofreportAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=updateSortOfReport";
		form.submit();
	}	
	
	function setUpdateHoursAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=refreshHours";
		form.submit();
	}	
	
	function setUpdatePeriodAction(form) {	
 		form.action = "/tb/do/StoreDailyReport?task=refreshPeriod";
		form.submit();
	}	
	
	function setStoreAction(form, actionVal, addMore) {
 		form.action = "/tb/do/StoreDailyReport?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}	
			
	function backToOverview(form) {	
 		form.action = "/tb/do/ShowDailyReport";
		form.submit();
	}
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<html:form action="/StoreDailyReport" styleId="StoreDailyReportForm">	
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message
		key="main.general.entertimereportproperties.text" />:<br></span>
	<br>
	<table width="100%" border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle" colspan="2">
				<span style="color:red"><html:errors property="employeeorder" /></span>
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.timereport.employee.fullname.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="employeeContractId" value="${currentEmployeeContract.id}" onchange="setUpdateOrdersAction(this.form)">				
				<c:choose>
					<c:when test="${employeeAuthorized}">
						<c:forEach var="employeecontract" items="${employeecontracts}" >
							<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
								<html:option value="${employeecontract.id}">
									<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if 
										test="${employeecontract.openEnd}"><bean:message 
										key="main.general.open.text" /></c:if>)
								</html:option>
							</c:if>							
						</c:forEach>
					</c:when>
					<c:otherwise>
						<c:forEach var="employeecontract" items="${employeecontracts}" >
							<c:if test="${employeecontract.employee.id == loginEmployee.id}">
								<html:option value="${employeecontract.id}">
									<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if 
										test="${employeecontract.openEnd}"><bean:message 
										key="main.general.open.text" /></c:if>)
								</html:option>
							</c:if>							
						</c:forEach>
						<%-- 
						<html:option value="${loginEmployeeContract.id}">
							<c:out value="${loginEmployeeContract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${loginEmployeeContract.timeString}" />)
						</html:option>
						 --%>
					</c:otherwise>
				</c:choose>	
				</html:select> 
				
			</td>
		</tr>

		<!-- Datumsauswahl -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.timereport.referenceday.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<!-- JavaScript Stuff for popup calender -->
				<script type="text/javascript" language="JavaScript" src="/tb/CalendarPopup.js">
				</script>
				<script type="text/javascript" language="JavaScript">
                    document.write(getCalendarStyles());
                </script>
                <script type="text/javascript" language="JavaScript">
                    function calenderPopup() {
                        var cal = new CalendarPopup();
                        
                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
                        // cal.select(document.forms[0].referenceday,'anchor1','E yyyy-MM-dd');
                        cal.select(document.forms[0].referenceday,'anchor1','yyyy-MM-dd');
                    }
                </script>
                <html:text property="referenceday" onblur="adjustBeginTimeAction(this.form)" 
                	readonly="false" size="10" maxlength="10" />
                <a href="javascript:calenderPopup()" name="anchor1" ID="anchor1" style="text-decoration:none;">
                	<img src="/tb/images/popupcalendar.gif" width="22" height="22" 
                		alt="<bean:message key="main.date.popup.alt.text" />"
						style="border:0;vertical-align:top"> 
				</a>
				
				<%-- Arrows for navigating the Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('-1')" title="<bean:message key="main.date.popup.prevday" />">
					<img src="/tb/images/pfeil_links.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				&nbsp;&nbsp;
				<a href="javascript:setDate('0')" title="<bean:message key="main.date.popup.today" />">
					<img src="/tb/images/pfeil_unten.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				&nbsp;&nbsp;
				<a href="javascript:setDate('1')" title="<bean:message key="main.date.popup.nextday" />">
					<img src="/tb/images/pfeil_rechts.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				<span style="color:red">
					<html:errors property="referenceday" />
					<html:errors property="release" />
				</span>
			</td>
		</tr>
		<!-- Serienbuchung -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.timereport.serialbooking.text" /></b>&nbsp;<i>(<bean:message key="main.timereport.labordays.text" />)</i><b>:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="numberOfSerialDays" >
					<html:options collection="serialBookings" labelProperty="label"	property="value" />
				</html:select>
				<span style="color:red">
					<html:errors property="serialbooking" />
				</span>
			</td>
		</tr>
		

		<logic:equal name="report" value="W" scope="session">
			<tr>
				<td align="left" class="noBborderStyle" nowrap="nowrap">
					<b><bean:message key="main.timereport.customerorder.text" />&nbsp;/&nbsp;<bean:message key="main.timereport.suborder.text" />:</b>
				</td>

				<td align="left" class="noBborderStyle" nowrap="nowrap"  width="90%" >
					<html:select property="orderId" onchange="setUpdateSubordersAction(this.form)">
						<html:options collection="orders" labelProperty="signAndDescription" property="id" />
					</html:select>
					<b> / </b>
					<html:select property="suborderSignId" styleClass="mandatory" value="${currentSuborderId}" onchange="adjustSuborderSignChangedAction(this.form)">
						<html:options collection="suborders" labelProperty="signAndDescription"	property="id" />
					</html:select>
					<span style="color:red">
						<html:errors property="orderId" />
						<html:errors property="suborderId" />
					</span>
				</td>
			</tr>
			<c:if test="${workingDayIsAvailable}">
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.timereport.begin.text" /></b>&nbsp;<i>(hh:mm)</i><b>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="selectedHourBegin" onchange="setUpdateHoursAction(this.form)">
							<html:options collection="hours" property="value" labelProperty="label" />
						</html:select>
						<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
						<html:select property="selectedMinuteBegin"	onchange="setUpdateHoursAction(this.form)">
							<html:options collection="minutes" property="value"	labelProperty="label" />
						</html:select>
						<i><bean:message key="main.timereport.optional.help.text" /></i>
						<span style="color:red">
							<html:errors property="selectedHourBegin" />
						</span>
					</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.timereport.end.text" /></b>&nbsp;<i>(hh:mm)</i><b>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="selectedHourEnd"	onchange="setUpdateHoursAction(this.form)">
							<html:options collection="hours" property="value" labelProperty="label" />
						</html:select>
						<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
						<html:select property="selectedMinuteEnd" onchange="setUpdateHoursAction(this.form)">
							<html:options collection="minutes" property="value"	labelProperty="label" />
						</html:select>
						<%--
						<b><bean:message key="main.timereport.orduration.text" />:</b>
	           		    <html:text property="hours" size="6" maxlength="6" onchange="setUpdatePeriodAction(this.form)"/>   
						 --%>
           			 	<i><bean:message key="main.timereport.optional.help.text" /></i>
           			 	<span style="color:red">
           			 		<html:errors property="selectedHourEnd" />
           			 	</span>
           			</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle">
						<i><bean:message key="main.timereport.or.text" /></i>
					</td>
				</tr>
			</c:if>
			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.timereport.duration.text" /></b>&nbsp;<i>(hh:mm)</i><b>:</b>
				</td>
				<td align="left" class="noBborderStyle">
					<html:select property="selectedHourDuration" onchange="setUpdatePeriodAction(this.form)">
						<html:options collection="hoursDuration" property="value" labelProperty="label" />
					</html:select>
					<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
					<html:select property="selectedMinuteDuration" onchange="setUpdatePeriodAction(this.form)">
						<html:options collection="minutes" property="value" labelProperty="label" />
					</html:select>
				</td>
			</tr>

			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.timereport.costs.text" />:</b>
				</td>
				<td align="left" class="noBborderStyle">
					<html:text property="costs" size="10" maxlength="8" />
				 	<span style="color:red">
				 		<html:errors property="costs" />
				 	</span>
				 </td>
			</tr>
			
		<%-- Training Flag --%>
			<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.timereport.training.text"/>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="training" /> 
					</td>
					
			</tr>
				
			<%--
     	   <tr>
           	  	<td align="left" class="noBborderStyle">
                	<b><bean:message key="main.timereport.status.text" /></b>
             	 </td>
            	<td align="left" class="noBborderStyle">
                	<html:select property="status">
						<html:option value="open"><bean:message key="main.timereport.select.status.open.text"/></html:option>
					</html:select> 
					 
                	<html:text property="status" size="30" maxlength="<%=\"\" + org.tb.GlobalConstants.STATUS_MAX_LENGTH %>"/>              
            		
            	<span style="color:red"><html:errors property="status"/></span>
            	</td>
      	  	</tr> 
			 --%>
		</logic:equal>
		<tr>
			<td align="left" valign="top" class="noBborderStyle">
				<b><bean:message key="main.timereport.comment.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:textarea property="comment" cols="30" rows="5" />
				<span style="color:red">
					<html:errors property="comment" />
				</span>
			</td>
		</tr>

	</table>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td class="noBborderStyle">
				<html:submit onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button">
					<bean:message key="main.general.button.save.text" />
				</html:submit>
			</td>
			<td class="noBborderStyle">
				<html:submit onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button">
        			<bean:message key="main.general.button.saveandcontinue.text" />
        		</html:submit>
        	</td>
			<td class="noBborderStyle">
				<html:submit onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button">
					<bean:message key="main.general.button.reset.text" />
				</html:submit>
			</td>
			<td class="noBborderStyle">
				<html:submit onclick="backToOverview(this.form)" styleId="button">
					<bean:message key="main.general.button.back.text" />
				</html:submit>
			</td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>
</html:html>
