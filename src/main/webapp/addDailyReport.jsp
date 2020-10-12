<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addtimereport.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<link href="/tb/style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />
<script src="/tb/scripts/jquery-1.11.3.min.js" type="text/javascript"></script>
<script src="/tb/scripts/select2.full.min.js" type="text/javascript"></script>
<script>
	var currentUser = "${currentEmployeeContract.employee.sign}";
</script>
<script src="/tb/scripts/favouriteOrder.js" type="text/javascript"></script>
<% java.text.DateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd");%>
<script type="text/javascript">
	if(typeof Storage !== "undefined") HBT.Salat.FavouriteOrders.initialize({
		thisIsTheDefaultOrder: '<bean:message key="add.report.this.default.order" />',
		otherIsTheDefaultOrder: '<bean:message key="add.report.other.default.order" arg0="{0}" />',
		thisIsTheDefaultSuborder: '<bean:message key="add.report.this.default.suborder" />',
		otherIsTheDefaultSuborder: '<bean:message key="add.report.other.default.suborder" arg0="{0}" />',
		noDefaultOrder: '<bean:message key="add.report.no.default.order" />',
		noDefaultSuborder: '<bean:message key="add.report.no.default.suborder" />',
		localStorageMsg: '<bean:message key="add.report.default.localStorage.msg" />'
	});

 	function setUpdateOrdersAction(form) {
 		form.action = "/tb/do/StoreDailyReport?task=refreshOrders";
		form.submit();
	}	
	
	function setUpdateSubordersAction(select) {	
 		var form = select.form;
 		
 		var orderIndex = select.options[select.selectedIndex].value;
 		var paramToAdd = "";
 		if(orderIndex && orderIndex != "0") {
	 		var suborderIndex = HBT.Salat.FavouriteOrders.getDefaultSuborder(orderIndex);
	 		if(suborderIndex && suborderIndex != "0") {
	 			paramToAdd = "&defaultSuborderIndex=" + suborderIndex;
	 		}
 		}
		
		form.action = "/tb/do/StoreDailyReport?task=refreshSuborders" + paramToAdd;
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
 		form.action = "/tb/do/ShowDailyReport?task=refreshTimereports";
		form.submit();
	}

	$(document).ready(function() {
		$(".ecCls").select2({
			dropdownAutoWidth: true,
			width: 'element'
		});	
		
		HBT.Salat.FavouriteOrders.initializeOrderSelection();
		HBT.Salat.FavouriteOrders.initializeSuborderSelection();
	});		
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
				<html:select property="employeeContractId" value="${currentEmployeeContract.id}" onchange="setUpdateOrdersAction(this.form)" styleClass="make-select2 ecCls">				
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
                    function hitEnter(e, form) {
                    	if(e.keyCode == 13 || e.which == 13) {
                    		adjustBeginTimeAction(form);
                    	}
                    }
                </script>
                <html:text property="referenceday" onchange="adjustBeginTimeAction(this.form)" 
                	readonly="false" size="10" maxlength="10" 
                	onkeyup="hitEnter(event, this.form);"/>
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
		
		<!-- Customerorder and Suborder (drop-down-menues) -->
		<logic:equal name="report" value="W" scope="session">
			<tr>
				<td align="left" class="noBborderStyle" nowrap="nowrap">
					<b><bean:message key="main.timereport.customerorder.text" />&nbsp;/&nbsp;<bean:message key="main.timereport.suborder.text" />:</b>
				</td>

				<td align="left" class="noBborderStyle" nowrap="nowrap"  width="90%" >
					<html:select property="orderId" onchange="setUpdateSubordersAction(this)" disabled="${projectIDExists and isEdit}" styleClass="make-select2 orderCls">
						<html:options collection="orders" labelProperty="signAndDescription" property="id" />
					</html:select>
					<img id="favOrderBtn" class="favOrderBtn" src="/tb/images/Button/whiteStar.svg" width="20" height="20" title="<bean:message key="add.report.no.default.order" />" onclick="HBT.Salat.FavouriteOrders.actionOrderSet(this);" />
					<b> / </b>
					<html:select property="suborderSignId" styleClass="mandatory make-select2 suborderCls" value="${currentSuborderId}" 
						onchange="adjustSuborderSignChangedAction(this.form)" disabled="${projectIDExists and isEdit}">
						<html:options collection="suborders" labelProperty="signAndDescription"	property="id" />
					</html:select>
					<img id="favSuborderBtn" class="favOrderBtn" src="/tb/images/Button/whiteStar.svg" width="20" height="20" title="<bean:message key="add.report.no.default.suborder" />" onclick="HBT.Salat.FavouriteOrders.actionSuborderSet(this);" />
					<span style="color:red">
						<html:errors property="orderId" />
						<html:errors property="suborderId" />
					</span>
				</td>
			</tr>
			
			<!-- Jira Ticket Keys, if chosen Customerorder has Project-ID(s) -->
			<c:if test="${projectIDExists}">
				<tr>
					<td align="left" class="noBborderStyle" nowrap="nowrap">
						<b><bean:message key="main.timereport.jiraTicketKeys.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle" nowrap="nowrap"  width="90%" >
						<html:select property="jiraTicketKey" value="${jiraTicketKey}" >
							<html:option value="-1">
								<bean:message key="main.timereport.jiraTicketKey.choose" />
							</html:option>
							<c:forEach var="jiraTicketKey" items="${jiraTicketKeys}">
									<html:option value="${jiraTicketKey}">
										<c:out value="${jiraTicketKey}" />
									</html:option>
								</c:forEach>
						</html:select>
						<span style="color:red">
				 			<html:errors property="noEmployeeOrderForJiraTicketKey" />
				 			<html:errors property="noTicketWithKeyAndDate" />
				 	</span>
					</td>
				</tr>
				<tr>	
					<td align="left" class="noBborderStyle" nowrap="nowrap">
						<b><bean:message key="main.timereport.newJiraTicketKey.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle" nowrap="nowrap"  width="90%" >
					<html:text property="newJiraTicketKey" size="10" maxlength="32" />
				 	<span style="color:red">
				 		<html:errors property="nonexistentKey" />
				 		<html:errors property="noKeySelected" />
				 		<html:errors property="newJiraTicketKeyErr" />
				 	</span>
				 	</td>
				</tr>
			</c:if>			
			
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
					<html:select property="selectedHourDuration" onchange="setUpdatePeriodAction(this.form)" disabled="${(not empty currentSuborderSign) and (currentSuborderSign eq overtimeCompensation)}">
						<html:options collection="hoursDuration" property="value" labelProperty="label" />
					</html:select>
					<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
					<html:select property="selectedMinuteDuration" onchange="setUpdatePeriodAction(this.form)" disabled="${(not empty currentSuborderSign) and (currentSuborderSign eq overtimeCompensation)}">
						<html:options collection="minutes" property="value" labelProperty="label" />
					</html:select>
					<span style="color:red">
           			 	<html:errors property="selectedDuration" />
           			 </span>
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
				
		</logic:equal>
		
		<tr>
			<td align="left" valign="top" class="noBborderStyle">
				<b><bean:message key="main.timereport.comment.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:textarea property="comment" cols="30" rows="5" /> <!-- value="${trComment}" -->
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
				<html:button property="save" onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button">
					<bean:message key="main.general.button.save.text" />
				</html:button>
			</td>
			<td class="noBborderStyle">
				<html:button property="saveAndContinue" onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button">
        			<bean:message key="main.general.button.saveandcontinue.text" />
        		</html:button>
        	</td>
			<td class="noBborderStyle">
				<html:button property="reset" onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button">
					<bean:message key="main.general.button.reset.text" />
				</html:button>
			</td>
			<td class="noBborderStyle">
				<html:button property="back" onclick="backToOverview(this.form)" styleId="button">
					<bean:message key="main.general.button.back.text" />
				</html:button>
			</td>
		</tr>
	</table>
	
	<br>
	
	<html:hidden property="id" />
	<span style="color:red">
		<html:errors property="general" />
		<html:errors property="status" />
	</span>
	
</html:form>
</body>
</html:html>
