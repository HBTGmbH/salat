<%@ page import="org.tb.bdom.Employee"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

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
<title><bean:message key="main.general.mainmenu.daily.text" /></title>
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
	
	function saveBegin(form) {
		form.action = "/tb/do/ShowDailyReport?task=saveBegin";
		form.submit();
	}
	
	function saveBreak(form) {
		form.action = "/tb/do/ShowDailyReport?task=saveBreak";
		form.submit();
	}					
 
</script>

</head>
<body>

<p>
<h2><bean:message key="main.general.mainmenu.daily.text" /></h2>
</p>
<br>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br><br><br><br>

<html:form action="/ShowDailyReport">
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor" >
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.employee.fullname.text" />:</b></td>
			<td align="left" class="noBborderStyle">
			<html:select
				property="employeename"
				value="<%=(String) request.getSession().getAttribute("currentEmployee")%>"
				onchange="setUpdateTimereportsAction(this.form)">
				
					<html:option value="ALL EMPLOYEES">
						<bean:message key="main.general.allemployees.text" />
					</html:option>
				
				<html:options collection="employeeswithcontract" labelProperty="name"
					property="name" />
			</html:select> <logic:equal name="currentEmployee" value="ALL EMPLOYEES"
				scope="session">
				<span style="color:red"> <b><bean:message
					key="main.general.selectemployee.editable.text" />.</b> </span>
			</logic:equal></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.customerorder.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="order"
				value="<%=(String) request.getSession().getAttribute("currentOrder")%>"
				onchange="setUpdateTimereportsAction(this.form)">

				<html:option value="ALL ORDERS">
					<bean:message key="main.general.allorders.text" />
				</html:option>

				<html:options collection="orders" labelProperty="sign"
					property="sign" />
				<html:hidden property="orderId" />
			</html:select></td>

		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.daymonthyear.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="day"
				value="<%=(String) request.getSession().getAttribute("currentDay")%>"
				onchange="setUpdateTimereportsAction(this.form)">
				<html:options collection="days" property="value"
					labelProperty="label" />
			</html:select> <html:select property="month"
				value="<%=(String) request.getSession().getAttribute("currentMonth")%>"
				onchange="setUpdateTimereportsAction(this.form)">
				<html:option value="Jan">
					<bean:message key="main.timereport.select.month.jan.text" />
				</html:option>
				<html:option value="Feb">
					<bean:message key="main.timereport.select.month.feb.text" />
				</html:option>
				<html:option value="Mar">
					<bean:message key="main.timereport.select.month.mar.text" />
				</html:option>
				<html:option value="Apr">
					<bean:message key="main.timereport.select.month.apr.text" />
				</html:option>
				<html:option value="May">
					<bean:message key="main.timereport.select.month.may.text" />
				</html:option>
				<html:option value="Jun">
					<bean:message key="main.timereport.select.month.jun.text" />
				</html:option>
				<html:option value="Jul">
					<bean:message key="main.timereport.select.month.jul.text" />
				</html:option>
				<html:option value="Aug">
					<bean:message key="main.timereport.select.month.aug.text" />
				</html:option>
				<html:option value="Sep">
					<bean:message key="main.timereport.select.month.sep.text" />
				</html:option>
				<html:option value="Oct">
					<bean:message key="main.timereport.select.month.oct.text" />
				</html:option>
				<html:option value="Nov">
					<bean:message key="main.timereport.select.month.nov.text" />
				</html:option>
				<html:option value="Dec">
					<bean:message key="main.timereport.select.month.dec.text" />
				</html:option>
			</html:select> <html:select property="year"
				value="<%=(String) request.getSession().getAttribute("currentYear")%>"
				onchange="setUpdateTimereportsAction(this.form)">
				<html:options collection="years" property="value"
					labelProperty="label" />
			</html:select></td>
		</tr>
		
		<c:if test="${currentEmployee != 'ALL EMPLOYEES'}">
			<tr>
				<td align="left" class="noBborderStyle"><b>Arbeitsbeginn:</b></td>
				<td align="left" class="noBborderStyle"><b><bean:message
					key="main.timereport.selectedhourbegin.text" />:</b> <html:select
					property="selectedWorkHourBegin">
					<html:options collection="hours" property="value"
						labelProperty="label" />
				</html:select> <b><bean:message key="main.timereport.selectedminutebegin.text" />:</b>
				<html:select property="selectedWorkMinuteBegin">
					<html:options collection="minutes" property="value"
						labelProperty="label" />
				</html:select></td>
				<td align="center" class="noBborderStyle"><html:image
					onclick="saveBegin(this.form)" src="/tb/images/Save.gif"
					alt="save start of work" /></td>
			</tr>
			<tr>
				<td align="left" class="noBborderStyle"><b>Pausendauer:</b></td>
				<td align="left" class="noBborderStyle"><b><bean:message
					key="main.timereport.selectedhourbegin.text" />:</b> <html:select
					property="selectedBreakHour">
					<html:options collection="breakhours" property="value"
						labelProperty="label" />
				</html:select> <b><bean:message key="main.timereport.selectedminutebegin.text" />:</b>
				<html:select property="selectedBreakMinute">
					<html:options collection="breakminutes" property="value"
						labelProperty="label" />
				</html:select></td>
				<td align="center" class="noBborderStyle"><html:image
					onclick="saveBreak(this.form)" src="/tb/images/Save.gif"
					alt="save break" /></td>
			</tr>
			
			<tr>
				<td align="left" class="noBborderStyle"><b>errechnete
				Feierabendszeit:</b></td>
				<td align="left" class="noBborderStyle"><b><c:out value="${quittingtime}"></c:out></b></td>
			</tr>
		</c:if>
	</table>
	
</html:form>
<br>
<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor" >
	
	<tr>
		<td colspan="5" class="noBborderStyle">&nbsp;</td>
		<td class="noBborderStyle" align="right" ><b>gesamt:</b></td>
		<c:choose><c:when test="${maxlabortime}">			
			<td align="center" color="red"><b><font color="red"><c:out value="${labortime}"></c:out></font></b></td>
		</c:when>
		<c:otherwise>
			<td align="center"><b><c:out value="${labortime}"></c:out></b></td>
		</c:otherwise>
		</c:choose>
		<td align="center"><b><c:out value="${dailycosts}"></c:out></b></td>
	</tr>
	
	<tr>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.employee.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.refday.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.sortofreport.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.customerorder.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.suborder.text" /></b></td>
		<td align="left" width="25%"><b><bean:message
			key="main.timereport.monthly.taskdescription.text" /></b></td>
		<td align="center"><b><bean:message
			key="main.timereport.monthly.hours.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.costs.text" /></b></td>
		<!--  
		<td align="left"> <b><bean:message key="main.timereport.monthly.status.text"/></b> </td>	
		-->
		<td align="left"><b><bean:message
			key="main.timereport.monthly.save.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.edit.text" /></b></td>
		<td align="left"><b><bean:message
			key="main.timereport.monthly.delete.text" /></b></td>
	</tr>
	<logic:iterate id="timereport" name="timereports">
		<html:form action="/UpdateDailyReport?trId=${timereport.id}">
			<tr>		
				<td><bean:write name="timereport"
						property="employeecontract.employee.name" /></td>
				<td
					title='<bean:write name="timereport" property="referenceday.name"/>'>
				<logic:equal name="timereport" property="referenceday.holiday"
					value="true">
					<span style="color:red"> <bean:write name="timereport"
						property="referenceday.dow" /> <bean:write name="timereport"
						property="referenceday.refdate" /> </span>
				</logic:equal> <logic:equal name="timereport" property="referenceday.holiday"
					value="false">
					<bean:write name="timereport" property="referenceday.dow" />
					<bean:write name="timereport" property="referenceday.refdate" />
				</logic:equal></td>
				<td align="center"><logic:equal name="timereport"
					property="sortofreport" value="W">
					<bean:message key="main.timereport.monthly.sortofreport.work.text" />
				</logic:equal> <logic:equal name="timereport" property="sortofreport" value="V">
					<bean:message
						key="main.timereport.monthly.sortofreport.vacation.text" />
				</logic:equal> <logic:equal name="timereport" property="sortofreport" value="S">
					<bean:message key="main.timereport.monthly.sortofreport.sick.text" />
				</logic:equal></td>
				<td
					title="<c:out value="${timereport.suborder.customerorder.description}"></c:out>">
				<c:out value="${timereport.suborder.customerorder.sign}"></c:out><br>
				</td>
				<td
					title="<c:out value="${timereport.suborder.description}"></c:out>">
				<c:out value="${timereport.suborder.sign}"></c:out><br>
				</td>
				
				<!-- visibility dependent on user and status -->
				
				<c:choose>
					<c:when test="${((loginEmployee.name == currentEmployee) && (timereport.status == 'open')) || ((loginEmployee.status == bl) && (timereport.status == 'commited'))}">
						
						<!-- Kommentar -->
						<td><html:textarea property="comment" cols="12" rows="1"
							value="${timereport.taskdescription}" /> 
								<!--  
	     		 				<html:text property="comment" size="10" maxlength="<%="" + org.tb.GlobalConstants.COMMENT_MAX_LENGTH %>" value="${timereport.taskdescription}"/> 
	     		 				-->
	     		 		</td>
						
						<!-- Dauer -->
						<td align=center nowrap>
							<html:select name="timereport" property="selectedDurationHour"
								value="${timereport.durationhours}">
								<html:options collection="hoursDuration" property="value"
									labelProperty="label" />
							</html:select>
							<html:select property="selectedDurationMinute"
								value="${timereport.durationminutes}">
								<html:options collection="minutes" property="value"
									labelProperty="label" />
							</html:select>
						</td>

						<!-- Kosten -->
						<td>
							<html:text property="costs" size="8" value="${timereport.costs}" />
						</td>
				
						<!-- Speichern -->
						<td align="center"><html:image
							onclick="confirmSave(this.form, ${timereport.id})"
							src="/tb/images/Save.gif" alt="Save Timereport" /></td>
							
						<!-- Aendern -->
						<td align="center"><html:link
							href="/tb/do/EditDailyReport?trId=${timereport.id}">
							<img src="/tb/images/Edit.gif" alt="Edit Timereport" />
							</html:link></td>
							
						<!-- Loeschen -->
						<td align="center"><html:image
							onclick="confirmDelete(this.form, ${timereport.id})"
							src="/tb/images/Delete.gif" alt="Delete Timereport" /> </td>
					
					</c:when>
					<c:otherwise>
					
						<!-- Kommentar -->
						<td>
							<c:out value="${timereport.taskdescription}"/>
	     		 		</td>
						
						<!-- Dauer -->
						<td align="center" nowrap>
							<c:out value="${timereport.durationhours}"/>:<c:out value="${timereport.durationminutes}"/>
						</td>

						<!-- Kosten -->
						<td align="center">
							<c:out value="${timereport.costs}"/>
						</td>
				
						<!-- Speichern -->
						<td align="center">
							<img src="/tb/images/verbot.gif" alt="Save Timereport" />
						</td>
							
						<!-- Aendern -->
						<td align="center">
							<img src="/tb/images/verbot.gif" alt="Edit Timereport" />
						</td>
							
						<!-- Loeschen -->
						<td align="center">
							<img src="/tb/images/verbot.gif" alt="Delete Timereport" />
						</td>
							
					</c:otherwise>
				</c:choose>
			</tr>
			
		</html:form>
	</logic:iterate>
	<tr>
		<td colspan="5" class="noBborderStyle">&nbsp;</td>
		<td class="noBborderStyle" align="right" ><b>gesamt:</b></td>
		<c:choose><c:when test="${maxlabortime}">			
			<td align="center" color="red"><b><font color="red"><c:out value="${labortime}"></c:out></font></b></td>
		</c:when>
		<c:otherwise>
			<td align="center"><b><c:out value="${labortime}"></c:out></b></td>
		</c:otherwise>
		</c:choose>
		<td align="center"><b><c:out value="${dailycosts}"></c:out></b></td>
	</tr>
	
	<!-- Add ist immer freigegeben - Berechtigung wird auf der addDailyReport nach Zeitraum und Auftrag geprueft -->
	<tr>
		<html:form action="/CreateDailyReport">
			<td class="noBborderStyle" colspan="6"><html:submit>
				<bean:message key="main.general.button.createnewreport.text" />
			</html:submit></td>
		</html:form>
	</tr>
	
</table>
<br>


<span style="color:red"> <b> <html:errors
	property="trSuborderId" /><br>
<html:errors property="selectedHourEnd" /><br>
<html:errors property="costs" /><br>
<html:errors property="status" /><br>
<html:errors property="comment" /> </b> </span>
<br>

<logic:notEqual name="currentEmployee" value="ALL EMPLOYEES"
	scope="session">
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">
		<tr>
			<td><b><bean:message
				key="main.general.button.monthlyhourbalance.text" />: </b></td>
			<td><logic:greaterEqual name="hourbalance" value="0.0"
				scope="session">
				<b><%=hourBalanceDisplay%></b>
			</logic:greaterEqual> <logic:lessThan name="hourbalance" value="0.0" scope="session">
				<b><span style="color:red"> <%=hourBalanceDisplay%> </span> </b>
			</logic:lessThan></td>
		</tr>
		<tr>
			<td><b><bean:message
				key="main.general.button.vacationused.text" />: </b></td>
			<td><b><%=vacation%></b></td>
		</tr>
	</table>
</logic:notEqual>
</body>
</html:html>
