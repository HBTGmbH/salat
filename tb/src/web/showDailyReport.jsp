<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.daily.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" media="all" />
<link rel="stylesheet" type="text/css" href="/tb/print.css"
	media="print" />
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
					
  	function setUpdateTimereportsAction(form) {	
 		form.action = "/tb/do/ShowDailyReport?task=refreshTimereports";
		form.submit();
	}	
	
	function showWMTT(Trigger,id) {
  	  wmtt = document.getElementById(id);
    	var hint;
   	 hint = Trigger.getAttribute("hint");
   	 //if((hint != null) && (hint != "")){
   	 	//wmtt.innerHTML = hint;
    	wmtt.style.display = "block";
   	 //}
	}

	function hideWMTT() {
		wmtt.style.display = "none";
	}
	
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.daily.text" /><br></span>
<br>
<html:form action="/ShowDailyReport">
	<table class="center backgroundcolor">

		<!-- select employeecontract -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.employee.fullname.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select 
				property="employeeContractId" value="${currentEmployeeContract.id}"
				onchange="setUpdateTimereportsAction(this.form)">

				<html:option value="-1">
					<bean:message key="main.general.allemployees.text" />
				</html:option>
				<c:forEach var="employeecontract" items="${employeecontracts}" >
					<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
						<html:option value="${employeecontract.id}">
							<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" />)
						</html:option>
					</c:if>							
				</c:forEach>
			</html:select> </td>
		</tr>

		<!-- select order -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.customerorder.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<html:select
					property="order"
					value="${currentOrder}"
					onchange="setUpdateTimereportsAction(this.form)">

					<html:option value="ALL ORDERS">
						<bean:message key="main.general.allorders.text" />
					</html:option>

					<html:options collection="orders" labelProperty="signAndDescription"
						property="sign" />
					<html:hidden property="orderId" />
				</html:select>
				
				<!-- select suborder -->
				<c:if test="${currentOrder != 'ALL ORDERS'}">
					<c:forEach var="order" items="${orders}" >
						<c:if test="${order.sign == currentOrder}">
							 / 
							<html:select
								property="suborderId"
								onchange="setUpdateTimereportsAction(this.form)">
								<html:option value="-1">
									<bean:message key="main.general.allsuborders.text" />
								</html:option>
								<html:options collection="suborders" labelProperty="signAndDescription"
									property="id" />							
							</html:select>						
						</c:if>
					</c:forEach>
				</c:if>
			</td>
		</tr>

		<!-- select view mode -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.general.timereport.view.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="view" onchange="setUpdateTimereportsAction(this.form)">

				<html:option value="day">
					<bean:message key="main.general.timereport.view.daily.text" />
				</html:option>
				<!--  
				<html:option value="week">
					Wochenansicht
				</html:option>
				-->
				<html:option value="month">
					<bean:message key="main.general.timereport.view.monthly.text" />
				</html:option>
				<!--  
				<html:option value="project">
					Projektansicht
				</html:option>
				-->
				<html:option value="custom">
					<bean:message key="main.general.timereport.view.custom.text" />
				</html:option>

				<html:hidden property="view" />
			</html:select></td>
		</tr>

		<!-- select first date -->
		<tr>
			<c:choose>
				<c:when test="${view eq 'month'}">
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.monthlyreport.monthyear.text" />:</b></td>
				</c:when>
				<c:otherwise>
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.monthlyreport.daymonthyear.text" />:</b></td>
				</c:otherwise>
			</c:choose>

			<td align="left" class="noBborderStyle"><c:if
				test="${!(view eq 'month')}">
				<html:select property="day"
					value="<%=(String) request.getSession().getAttribute("currentDay")%>"
					onchange="setUpdateTimereportsAction(this.form)">
					<html:options collection="days" property="value"
						labelProperty="label" />
				</html:select>
			</c:if> <html:select property="month"
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


		<!-- select second date -->
		<c:if test="${view eq 'custom'}">
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
					key="main.monthlyreport.daymonthyear.text" />:</b></td>
				<td align="left" class="noBborderStyle"><html:select
					property="lastday" value="${lastDay}"
					onchange="setUpdateTimereportsAction(this.form)">
					<html:options collection="days" property="value"
						labelProperty="label" />
				</html:select> <html:select property="lastmonth" value="${lastMonth}"
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
				</html:select> <html:select property="lastyear" value="${lastYear}"
					onchange="setUpdateTimereportsAction(this.form)">
					<html:options collection="years" property="value"
						labelProperty="label" />
				</html:select></td>
			</tr>
		</c:if>

		<!-- select working day begin and break -->
		<c:if test="${view eq 'day' || view == null}">
			<c:if test="${currentEmployee != 'ALL EMPLOYEES'}">
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.timereport.startofwork.text" /></b> <i>(hh:mm)</i><b>:</b></td>
					<td align="left" class="noBborderStyle"><html:select
						property="selectedWorkHourBegin">
						<html:options collection="hours" property="value"
							labelProperty="label" />
					</html:select><b>&nbsp;&nbsp;:&nbsp;&nbsp;</b> <html:select
						property="selectedWorkMinuteBegin">
						<html:options collection="minutes" property="value"
							labelProperty="label" />
					</html:select>&nbsp;&nbsp;<html:image onclick="saveBegin(this.form)"
						src="/tb/images/Save.gif" alt="save start of work" />&nbsp;&nbsp;<i>(optional)</i></td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.timereport.breakduration.text" /></b> <i>(hh:mm)</i><b>:</b></td>
					<td align="left" class="noBborderStyle"><html:select
						property="selectedBreakHour">
						<html:options collection="breakhours" property="value"
							labelProperty="label" />
					</html:select><b>&nbsp;&nbsp;:&nbsp;&nbsp;</b> <html:select
						property="selectedBreakMinute">
						<html:options collection="breakminutes" property="value"
							labelProperty="label" />
					</html:select>&nbsp;&nbsp;<html:image onclick="saveBreak(this.form)"
						src="/tb/images/Save.gif" alt="save break" />&nbsp;&nbsp;<i>(optional)</i></td>
				</tr>

				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.timereport.quittingtime.text" />:</b></td>
					<td align="left" class="noBborderStyle"><b><c:out
						value="${quittingtime}"></c:out></b></td>
				</tr>
			</c:if>
		</c:if>
	</table>
</html:form>

<bean:size id="timereportsSize" name="timereports" />
<c:if test="${timereportsSize>10}">
	<table>
		<tr>
			<html:form action="/CreateDailyReport">
				<td class="noBborderStyle" colspan="6" align="left"><html:submit
					styleId="button" titleKey="main.general.button.createnewreport.alttext.text">
						<bean:message key="main.general.button.createnewreport.text" />
				</html:submit></td>
			</html:form>
			<html:form target="fenster"
				onsubmit="window.open('','fenster','width=800,height=400,resizable=yes')"
				action="/ShowDailyReport?task=print">
				<td class="noBborderStyle" colspan="6" align="left"><html:submit
					styleId="button" titleKey="main.general.button.printpreview.alttext.text">
					<bean:message key="main.general.button.printpreview.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</table>
</c:if>

<table class="center backgroundcolor" width="100%">

	<tr>
		<td colspan="5" class="noBborderStyle">&nbsp;</td>
		<td class="noBborderStyle" align="right"><b><bean:message
			key="main.timereport.total.text" />:</b></td>
		<c:choose>
			<c:when
				test="${maxlabortime && view eq 'day' && !(currentEmployee eq 'ALL EMPLOYEES')}">
				<th align="center" color="red"><b><font color="red"><c:out
					value="${labortime}"></c:out></font></b></th>
			</c:when>
			<c:otherwise>
				<th align="center"><b><c:out value="${labortime}"></c:out></b>
				</th>
			</c:otherwise>
		</c:choose>
		<th align="center"><b><c:out value="${dailycosts}"></c:out></b></th>
	</tr>

	<tr>
		<th align="left"><b>Info</b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.employee.text" />"><html:link
			href="/tb/do/ShowDailyReport?task=sort&column=employee">
			<b><bean:message key="main.timereport.monthly.employee.sign.text" /></b>
		</html:link> <c:if test="${timereportSortColumn eq 'employee'}">
			<c:out value="${timereportSortModus}" />
		</c:if></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.refday.text" />"><html:link
			href="/tb/do/ShowDailyReport?task=sort&column=refday">
			<b><bean:message key="main.timereport.monthly.refday.text" /></b>
		</html:link> <c:if test="${timereportSortColumn eq 'refday'}">
			<c:out value="${timereportSortModus}" />
		</c:if></th>
		<!--  
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.sortofreport.text" />"><b><bean:message
			key="main.timereport.monthly.sortofreport.text" /></b></th>
		-->
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.customerorder.text" />"><html:link
			href="/tb/do/ShowDailyReport?task=sort&column=order">
			<b><bean:message key="main.timereport.monthly.customerorder.text" /></b>
		</html:link> <c:if test="${timereportSortColumn eq 'order'}">
			<c:out value="${timereportSortModus}" />
		</c:if></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.description.text" />"><b><bean:message
			key="main.customerorder.shortdescription.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.taskdescription.text" />"
			width="25%"><b><bean:message
			key="main.timereport.monthly.taskdescription.text" /></b></th>
		<th align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.hours.text" />"><b><bean:message
			key="main.timereport.monthly.hours.text" /></b></th>
		<th align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.costs.text" />"><b><bean:message
			key="main.timereport.monthly.costs.text" /></b></th>
		<!--  
		<th align="left"> <b><bean:message key="main.timereport.monthly.status.text"/></b> </th>	
		-->
		<!-- 
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.save.text" />"><b><bean:message
			key="main.timereport.monthly.save.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.edit.text" />"><b><bean:message
			key="main.timereport.monthly.edit.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.delete.text" />"><b><bean:message
			key="main.timereport.monthly.delete.text" /></b></th>
		 -->
		<th align="center"
			title="<bean:message
			key="main.headlinedescription.dailyoverview.saveeditdelete.text" />"><b><bean:message
			key="main.timereport.monthly.saveeditdelete.text" /></b></th>
	</tr>

	<c:forEach var="timereport" items="${timereports}" varStatus="statusID">

		<html:form action="/UpdateDailyReport?trId=${timereport.id}">
			<c:choose>
				<c:when test="${statusID.count%2==0}">
					<tr class="primarycolor">
				</c:when>
				<c:otherwise>
					<tr class="secondarycolor">
				</c:otherwise>
			</c:choose>

			<!-- Info -->
			<td align="center">
			<div class="tooltip" id="info<c:out value='${timereport.id}' />">
			<table>
				<tr>
					<td class="info"><bean:message
						key="main.timereport.tooltip.employee" />:</td>
					<td class="info" colspan="3"><c:out
						value="${timereport.employeecontract.employee.name}" />&nbsp;&nbsp;(<c:out
						value="${timereport.employeecontract.timeString}" />)</td>
				</tr>
				<tr>
					<td class="info"><bean:message
						key="main.timereport.tooltip.order" />:</td>
					<td class="info" colspan="3"><c:out
						value="${timereport.suborder.customerorder.sign}" /></td>
				</tr>
				<tr>
					<td class="info">&nbsp;</td>
					<td class="info" colspan="3"><c:out
						value="${timereport.suborder.customerorder.description}" /></td>
				</tr>
				<tr>
					<td class="info"><bean:message
						key="main.timereport.tooltip.suborder" />:</td>
					<td class="info" colspan="3"><c:out
						value="${timereport.suborder.sign}" /></td>
				</tr>
				<tr>
					<td class="info">&nbsp;</td>
					<td class="info" colspan="3"><c:out
						value="${timereport.suborder.description}" /></td>
				</tr>
				<tr>
					<td class="info"><bean:message
						key="main.timereport.tooltip.status" />:</td>
					<td class="info"><c:out value="${timereport.status}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.created" />:</td>
					<td class="info"><c:out value="${timereport.created}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${timereport.createdby}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.edited" />:</td>
					<td class="info"><c:out value="${timereport.lastupdate}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${timereport.lastupdatedby}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.released" />:</td>
					<td class="info"><c:out value="${timereport.released}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${timereport.releasedby}" /></td>
				</tr>
				<tr>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.accepted" />:</td>
					<td class="info"><c:out value="${timereport.accepted}" /></td>
					<td class="info" valign="top"><bean:message
						key="main.timereport.tooltip.by" /></td>
					<td class="info" valign="top"><c:out
						value="${timereport.acceptedby}" /></td>
				</tr>
			</table>

			</div>
			<img
				onMouseOver="showWMTT(this,'info<c:out value="${timereport.id}" />')"
				onMouseOut="hideWMTT()" width="12px" height="12px"
				src="/tb/images/info_button.gif" />
			<c:if test="${!timereport.fitsToContract}">
				<img width="20px" height="20px" src="/tb/images/Pin rot.gif" title="<bean:message
						key="main.timereport.warning.datedoesnotfit" />"/>
			</c:if>
			</td>

			<!-- Mitarbeiter -->
			<td title="<c:out value="${timereport.employeecontract.employee.name}" />&nbsp;&nbsp;(<c:out value="${timereport.employeecontract.timeString}" />)"><c:out value="${timereport.employeecontract.employee.sign}" /></td>

			<!-- Datum -->
			<td title='<c:out value="${timereport.referenceday.name}" />'><logic:equal
				name="timereport" property="referenceday.holiday" value="true">
				<span style="color:red"> <bean:message
					key="${timereport.referenceday.dow}" /><br>
				<c:out value="${timereport.referenceday.refdate}" /></span>
			</logic:equal> <logic:equal name="timereport" property="referenceday.holiday"
				value="false">
				<bean:message key="${timereport.referenceday.dow}" />
				<br>
				<c:out value="${timereport.referenceday.refdate}" />
			</logic:equal></td>

			<!-- Typ -->
			<!--  
			<td align="center"><logic:equal name="timereport"
				property="sortofreport" value="W">
				<bean:message key="main.timereport.monthly.sortofreport.work.text" />
			</logic:equal> <logic:equal name="timereport" property="sortofreport" value="V">
				<bean:message
					key="main.timereport.monthly.sortofreport.vacation.text" />
			</logic:equal> <logic:equal name="timereport" property="sortofreport" value="S">
				<bean:message key="main.timereport.monthly.sortofreport.sick.text" />
			</logic:equal></td>
			-->

			<!-- Auftrag -->
			<td
				title="<c:out value="${timereport.suborder.customerorder.description}"></c:out>">
			<c:out value="${timereport.suborder.customerorder.sign}" /><br>
			<c:out value="${timereport.suborder.sign}" /></td>

			<!-- Bezeichnung -->
			<td><c:out
				value="${timereport.suborder.customerorder.shortdescription}" /><br>
			<c:out value="${timereport.suborder.shortdescription}" /></td>

			<!-- visibility dependent on user and status -->

			<c:choose>
				<c:when
					test="${((loginEmployee == timereport.employeecontract.employee) && (timereport.status eq 'open')) || ((loginEmployee.status eq 'bl' || loginEmployee.status eq 'gf') && (timereport.status eq 'commited')) || loginEmployee.status eq 'adm'}">

					<!-- Kommentar -->
					<td><html:textarea property="comment" cols="30" rows="1"
						value="${timereport.taskdescription}" /> <!--  
	     		 				<html:text property="comment" size="10" maxlength="<%="" + org.tb.GlobalConstants.COMMENT_MAX_LENGTH %>" value="${timereport.taskdescription}"/> 
	     		 				--></td>

					<!-- Dauer -->
					<td align="center" nowrap="nowrap"><html:select
						name="timereport" property="selectedDurationHour"
						value="${timereport.durationhours}">
						<html:options collection="hoursDuration" property="value"
							labelProperty="label" />
					</html:select> <html:select property="selectedDurationMinute"
						value="${timereport.durationminutes}">
						<html:options collection="minutes" property="value"
							labelProperty="label" />
					</html:select></td>

					<!-- Kosten -->
					<td align="center"><html:text property="costs" size="4"
						value="${timereport.costs}" /></td>

					<!--  
					<td align="center"><html:image
						onclick="confirmSave(this.form, ${timereport.id})"
						src="/tb/images/Save.gif" alt="Save Timereport" /></td>

					
					<td align="center"><html:link
						href="/tb/do/EditDailyReport?trId=${timereport.id}">
						<img src="/tb/images/Edit.gif" alt="Edit Timereport" />
					</html:link></td>

					
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${timereport.id})"
						src="/tb/images/Delete.gif" alt="Delete Timereport" /></td>
					-->

					<!-- Bearbeiten -->
					<td align="center"><html:image
						onclick="confirmSave(this.form, ${timereport.id})"
						src="/tb/images/Save.gif" alt="Speichern" title="Speichern" />
					&nbsp; <html:link title="Ändern"
						href="/tb/do/EditDailyReport?trId=${timereport.id}">
						<img src="/tb/images/Edit.gif" alt="Ändern" />
					</html:link> &nbsp; <html:image
						onclick="confirmDelete(this.form, ${timereport.id})"
						src="/tb/images/Delete.gif" alt="Löschen" title="Löschen" /></td>

				</c:when>
				<c:otherwise>

					<!-- Kommentar -->
					<td><c:choose>
						<c:when test="${timereport.taskdescription eq ''}">
								&nbsp;
							</c:when>
						<c:otherwise>
							<c:out value="${timereport.taskdescription}" />
						</c:otherwise>
					</c:choose></td>

					<!-- Dauer -->
					<td align="center" nowrap><c:if
						test="${timereport.durationhours < 10}">0</c:if><c:out
						value="${timereport.durationhours}" />:<c:if
						test="${timereport.durationminutes < 10}">0</c:if><c:out
						value="${timereport.durationminutes}" /></td>

					<!-- Kosten -->
					<td align="center"><c:out value="${timereport.costs}" /></td>

					<!-- 
					<td align="center"><img width="12px" height="12px" src="/tb/images/verbot.gif"
						alt="Save Timereport" /></td>

					
					<td align="center"><img width="12px" height="12px" src="/tb/images/verbot.gif"
						alt="Edit Timereport" /></td>

					
					<td align="center"><img width="12px" height="12px" src="/tb/images/verbot.gif"
						alt="Delete Timereport" /></td>
					-->

					<!-- Bearbeiten -->
					<td align="center"><img width="12px" height="12px"
						src="/tb/images/verbot.gif" alt="Delete Timereport" /></td>

				</c:otherwise>
			</c:choose>
			</tr>

		</html:form>
	</c:forEach>
	<tr>
		<td colspan="5" class="noBborderStyle">&nbsp;</td>
		<td class="noBborderStyle" align="right"><b><bean:message
			key="main.timereport.total.text" />:</b></td>
		<c:choose>
			<c:when
				test="${maxlabortime && view eq 'day' && !(currentEmployee eq 'ALL EMPLOYEES')}">
				<th align="center" color="red"><b><font color="red"><c:out
					value="${labortime}"></c:out></font></b></th>
			</c:when>
			<c:otherwise>
				<th align="center"><b><c:out value="${labortime}"></c:out></b>
				</th>
			</c:otherwise>
		</c:choose>
		<th align="center"><b><c:out value="${dailycosts}"></c:out></b></th>
	</tr>
</table>
<table>
	<tr>		
		<html:form action="/CreateDailyReport">
			<td class="noBborderStyle" colspan="6" align="left"><html:submit
				styleId="button" titleKey="main.general.button.createnewreport.alttext.text">
				<bean:message key="main.general.button.createnewreport.text" />
			</html:submit></td>
		</html:form>	
		<html:form target="fenster"
			onsubmit="window.open('','fenster','width=800,height=400,resizable=yes')"
			action="/ShowDailyReport?task=print">
			<td class="noBborderStyle" colspan="6" align="left"><html:submit
				styleId="button" titleKey="main.general.button.printpreview.alttext.text">
				<bean:message key="main.general.button.printpreview.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
<br>

<span style="color:red"> <b> <html:errors
	property="trSuborderId" footer="<br>" /> <html:errors
	property="selectedHourEnd" footer="<br>" /> <html:errors
	property="costs" footer="<br>" /> <html:errors property="status"
	footer="<br>" /><br>
<html:errors property="comment" footer="<br>" /></b></span>

<!-- Überstunden und Urlaubstage -->

<logic:notEqual name="currentEmployee" value="ALL EMPLOYEES"
	scope="session">

	<jsp:include flush="true" page="/info2.jsp">
		<jsp:param name="info" value="Info" />
	</jsp:include>

</logic:notEqual>


</body>
</html:html>
