<%@ page language="java" contentType="text/html; charset=ISO-8859-1"  pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/xml" prefix="x"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="statusreport.edit.pagetitle.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<link href="/tb/style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />
<script src="/tb/scripts/jquery-1.11.3.min.js"></script>
<script src="/tb/scripts/select2.full.min.js"></script>

<script type="text/javascript" language="JavaScript">
	
	function setDate(which, howMuch) {
		document.forms[0].action = "/tb/do/StoreStatusReport?task=setDate&which=" + which + "&howMuch=" + howMuch;
		document.forms[0].submit();
	}
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreStatusReport?action=" + actionVal;
		form.submit();
	}
			
	function afterCalenderClick() {
	}
	
	// textarea limitation
	function limitText(limitField, limitNum) {
		if (limitField.value.length > limitNum) {
			limitField.value = limitField.value.substring(0, limitNum);
		} 
	}

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'element'
		});	
	});		
</script>
</head>
<body>
<html:form action="/StoreStatusReport">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br>
	<bean:message key="main.general.statusreportproperties.text" />:<br>
	</span>
	<br>
	<br>
	<div style="font-size: 12pt;"><i><c:out value="${actionInfo}" />&nbsp;</i></div>
	<br>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor" width="100%">
		
		<!-- Buttons -->
		<tr><td colspan="4" class="noBborderStyle">
			<html:submit onclick="setStoreAction(this.form, 'back')"
				styleId="button"><bean:message key="statusreport.button.back.text"/></html:submit>
			<html:submit onclick="setStoreAction(this.form, 'save')"
				styleId="button"><bean:message key="statusreport.button.save.text"/></html:submit>
			<c:if test="${isReportReadyForRelease}"><html:submit onclick="setStoreAction(this.form, 'release')"
				styleId="button"><bean:message key="statusreport.button.release.text" /></html:submit></c:if>
			<c:if test="${isReportReadyForAcceptance}"><html:submit onclick="setStoreAction(this.form, 'accept')"
				styleId="button"><bean:message key="statusreport.button.accept.text" /></html:submit></c:if>
			<c:if test="${loginEmployee.status == 'adm' && currentStatusReport != null && currentStatusReport.released != null}"><html:submit 
				onclick="setStoreAction(this.form, 'removeRelease')"
				styleId="button"><bean:message key="statusreport.button.removerelease.text" /></html:submit></c:if>	
			<br>&nbsp;
		</td></tr>
		
		<!-- Headline -->
		<tr>
			<th align="left" colspan="4">
				<c:out value="${reportStatus}" />&nbsp;
			</th>
		</tr>
		
		<!-- overall status -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.overallstatus.text" />:</b>
			</td>
			<td class="noBborderStyle" nowrap="nowrap" colspan="3">
				<html:radio property="overallStatus" value="3" disabled="true"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio property="overallStatus" value="2" disabled="true"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;			
				<html:radio property="overallStatus" value="1" disabled="true"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>							
			</td>			
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- order -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.order.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<html:select property="customerOrderId" disabled="${!isReportEditable}" onchange="setStoreAction(this.form, 'refresh')" styleClass="make-select2">
					<html:options collection="visibleCustomerOrders" labelProperty="signAndDescription" property="id"/>
				</html:select>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle">&nbsp;</td></tr>
		
		<!-- sort -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.sort.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<html:select property="sort" disabled="${!isReportEditable}" styleClass="make-select2">
					<html:options collection="sorts" labelProperty="label" property="value"/>
				</html:select>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle">&nbsp;</td></tr>
		
		<!-- sender -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.from.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<html:select property="senderId" disabled="${!isReportEditable}" styleClass="make-select2">
					<html:options collection="employees" labelProperty="name" property="id"/>
				</html:select>
			</td>
		</tr>
		
		<!-- recipient -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.to.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<html:select property="recipientId" disabled="${!isReportEditable}" styleClass="make-select2">
					<html:options collection="employees" labelProperty="name" property="id"/>
				</html:select>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle">&nbsp;</td></tr>
		
		<!-- phase -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.phase.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<html:select property="phase" disabled="${!isReportEditable}" styleClass="make-select2">
					<html:options collection="phases" labelProperty="label" property="value"/>
				</html:select>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle">&nbsp;</td></tr>
		
		<!-- time period -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left" rowspan="2">
				<b><bean:message key="statusreport.timeperiod.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" width="1%">
				<b><bean:message key="statusreport.from.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="2"> 
				<!-- JavaScript Stuff for popup calender -->
				<script type="text/javascript" language="JavaScript"
					src="/tb/CalendarPopup.js"></script> <script type="text/javascript"
					language="JavaScript">document.write(getCalendarStyles());
                </script>
				<script type="text/javascript" language="JavaScript">
                    function calenderPopup(cal) {
                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
                    }
                    function calenderPopupFrom() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        cal.select(document.forms[0].validFrom,'from','yyyy-MM-dd');
                    }
                    function calenderPopupUntil() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        cal.select(document.forms[0].validUntil,'until','yyyy-MM-dd');
                    }
                </script>
				<html:text property="validFrom"  
					size="10" maxlength="10" disabled="${!isReportEditable}" /> <c:if test="${isReportEditable}"><a
					href="javascript:calenderPopupFrom()" name="from" ID="from"
					style="text-decoration:none;"> <img
					src="/tb/images/popupcalendar.gif" width="22" height="22"
					alt="<bean:message key="main.date.popup.alt.text" />"
					style="border:0;vertical-align:top"> </a></c:if>
					
				<%-- Arrows for navigating the from-Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','-1')" title="<bean:message key="main.date.popup.prevday" />">
				<img src="/tb/images/pfeil_links.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','0')" title="<bean:message key="main.date.popup.today" />">
				<img src="/tb/images/pfeil_unten.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('from','1')" title="<bean:message key="main.date.popup.nextday" />">
				<img src="/tb/images/pfeil_rechts.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
					
				<span style="color:red"><html:errors property="fromdate" /></span>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" valign="top" align="left" width="1%">
				<b><bean:message key="statusreport.until.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="2">
				<html:text property="validUntil"  
					size="10" maxlength="10" disabled="${!isReportEditable}" /> <c:if test="${isReportEditable}"><a 
					href="javascript:calenderPopupUntil()" name="until" ID="until"
					style="text-decoration:none;" > <img
					src="/tb/images/popupcalendar.gif" width="22" height="22"
					alt="<bean:message key="main.date.popup.alt.text" />"
					style="border:0;vertical-align:top"> </a></c:if>
					
				<%-- Arrows for navigating the until-Date --%>
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','-1')" title="<bean:message key="main.date.popup.prevday" />">
				<img src="/tb/images/pfeil_links.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','0')" title="<bean:message key="main.date.popup.today" />">
				<img src="/tb/images/pfeil_unten.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				&nbsp;&nbsp;
				<a href="javascript:setDate('until','1')" title="<bean:message key="main.date.popup.nextday" />">
				<img src="/tb/images/pfeil_rechts.gif" height="11px" width="11px" style="border:0;vertical-align:middle" />
				</a>
				
				<span style="color:red"><html:errors property="untildate" /></span>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle">&nbsp;</td></tr>
		
		<!-- allocator -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.allocator.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<html:text property="allocator" maxlength="64" size="64" disabled="${!isReportEditable}" />
				<span style="font-size: 7pt;">(max 64)</span>
				<span style="color:red"><html:errors property="allocator" /></span>
			</td>
		</tr>
				
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- trend -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>1. <bean:message key="statusreport.summary.text" />:</b>
			</td>
			
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<b><bean:message key="statusreport.trend.text"/></b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio titleKey="statusreport.trend.constant.text" property="trend" disabled="${!isReportEditable}" value="1"><html:img titleKey="statusreport.trend.constant.text" style="width:15px; height:15px;" altKey="statusreport.trend.constant.text" src="/tb/images/arrow_right2.gif" /></html:radio>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio titleKey="statusreport.trend.up.text" property="trend" disabled="${!isReportEditable}" value="2"><html:img titleKey="statusreport.trend.up.text" style="width:15px; height:15px;" altKey="statusreport.trend.up.text" src="/tb/images/arrow_diagonal_up2.gif" /></html:radio>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio titleKey="statusreport.trend.straightup.text" property="trend" disabled="${!isReportEditable}" value="3"><html:img titleKey="statusreport.trend.straightup.text" style="width:15px; height:15px;" altKey="statusreport.trend.straightup.text" src="/tb/images/arrow_up2.gif" /></html:radio>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio titleKey="statusreport.trend.down.text" property="trend" disabled="${!isReportEditable}" value="4"><html:img titleKey="statusreport.trend.down.text" style="width:15px; height:15px;" altKey="statusreport.trend.down.text" src="/tb/images/arrow_diagonal_down2.gif" /></html:radio>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio titleKey="statusreport.trend.straightdown.text" property="trend" disabled="${!isReportEditable}" value="5"><html:img titleKey="statusreport.trend.straightdown.text" style="width:15px; height:15px;" altKey="statusreport.trend.straightdown.text" src="/tb/images/arrow_down2.gif" /></html:radio>
				<span style="color:red"><br><html:errors property="trend" /></span>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="trendstatus" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="trendstatus" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="trendstatus" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>					
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- need for action -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>2. <bean:message key="statusreport.needforaction.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="needforaction_text" /></span><br>
				<html:textarea property="needforaction_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.needforaction_text,2048);" 
					onkeyup="limitText(this.form.needforaction_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="needforaction_source" /></span><br>
				<html:textarea property="needforaction_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.needforaction_source,256);" 
					onkeyup="limitText(this.form.needforaction_source,256);"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="needforaction_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="needforaction_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="needforaction_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>			
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- aim -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>3. <bean:message key="statusreport.aim.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="aim_text" /></span><br>
				<html:textarea property="aim_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.aim_text,2048);" 
					onkeyup="limitText(this.form.aim_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="aim_source" /></span><br>
				<html:textarea property="aim_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.aim_source,256);" 
					onkeyup="limitText(this.form.aim_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="aim_action" /></span><br>
				<html:textarea property="aim_action" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.aim_action,256);" 
					onkeyup="limitText(this.form.aim_action,256);"/>
			</td>
		</tr>
		<tr>
		<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="aim_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="aim_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="aim_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>		
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- budget resources date -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>4. <bean:message key="statusreport.budget_resources_date.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="budget_resources_date_text" /></span><br>
				<html:textarea property="budget_resources_date_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.budget_resources_date_text,2048);" 
					onkeyup="limitText(this.form.budget_resources_date_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="budget_resources_date_source" /></span><br>
				<html:textarea property="budget_resources_date_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.budget_resources_date_source,256);" 
					onkeyup="limitText(this.form.budget_resources_date_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="budget_resources_date_action" /></span><br>
				<html:textarea property="budget_resources_date_action" cols="100" rows="4" readonly="${!isReportEditable}"
					onkeydown="limitText(this.form.budget_resources_date_action,256);" 
					onkeyup="limitText(this.form.budget_resources_date_action,256);" />
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="budget_resources_date_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="budget_resources_date_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="budget_resources_date_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- risk monitoring -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>5. <bean:message key="statusreport.riskmonitoring.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="riskmonitoring_text" /></span><br>
				<html:textarea property="riskmonitoring_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.riskmonitoring_text,2048);" 
					onkeyup="limitText(this.form.riskmonitoring_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="riskmonitoring_source" /></span><br>
				<html:textarea property="riskmonitoring_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.riskmonitoring_source,256);" 
					onkeyup="limitText(this.form.riskmonitoring_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="riskmonitoring_action" /></span><br>
				<html:textarea property="riskmonitoring_action" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.riskmonitoring_action,256);" 
					onkeyup="limitText(this.form.riskmonitoring_action,256);"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="riskmonitoring_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="riskmonitoring_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="riskmonitoring_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- change directive -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>6. <bean:message key="statusreport.changedirective.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="changedirective_text" /></span><br>
				<html:textarea property="changedirective_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.changedirective_text,2048);" 
					onkeyup="limitText(this.form.changedirective_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="changedirective_source" /></span><br>
				<html:textarea property="changedirective_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.changedirective_source,256);" 
					onkeyup="limitText(this.form.changedirective_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="changedirective_action" /></span><br>
				<html:textarea property="changedirective_action" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.changedirective_action,256);" 
					onkeyup="limitText(this.form.changedirective_action,256);"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="changedirective_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="changedirective_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="changedirective_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- communication -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b>7. <bean:message key="statusreport.communication.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="communication_text" /></span><br>
				<html:textarea property="communication_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.communication_text,2048);" 
					onkeyup="limitText(this.form.communication_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="communication_source" /></span><br>
				<html:textarea property="communication_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.communication_source,256);" 
					onkeyup="limitText(this.form.communication_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="communication_action" /></span><br>
				<html:textarea property="communication_action" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.communication_action,256);" 
					onkeyup="limitText(this.form.communication_action,256);"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="communication_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="communication_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="communication_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- improvement -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>8. <bean:message key="statusreport.improvement.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="improvement_text" /></span><br>
				<html:textarea property="improvement_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.improvement_text,2048);" 
					onkeyup="limitText(this.form.improvement_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="improvement_source" /></span><br>
				<html:textarea property="improvement_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.improvement_source,256);" 
					onkeyup="limitText(this.form.improvement_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="improvement_action" /></span><br>
				<html:textarea property="improvement_action" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.improvement_action,256);" 
					onkeyup="limitText(this.form.improvement_action,256);"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="improvement_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="improvement_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="improvement_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- customer feedback -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>9. <bean:message key="statusreport.customerfeedback.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="customerfeedback_text" /></span><br>
				<html:textarea property="customerfeedback_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.customerfeedback_text,2048);" 
					onkeyup="limitText(this.form.customerfeedback_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="customerfeedback_source" /></span><br>
				<html:textarea property="customerfeedback_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.customerfeedback_source,256);" 
					onkeyup="limitText(this.form.customerfeedback_source,256);"/><br>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="customerfeedback_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="customerfeedback_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="customerfeedback_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
	
		
		<!-- miscellaneous -->
		<tr>			
			<td class="noBborderStyle" valign="top" align="left">
				<b>10. <bean:message key="statusreport.miscellaneous.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3" rowspan="2">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="miscellaneous_text" /></span><br>
				<html:textarea property="miscellaneous_text" cols="100" rows="10" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.miscellaneous_text,2048);" 
					onkeyup="limitText(this.form.miscellaneous_text,2048);"/><br>
				<bean:message key="statusreport.source.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="miscellaneous_source" /></span><br>
				<html:textarea property="miscellaneous_source" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.miscellaneous_source,256);" 
					onkeyup="limitText(this.form.miscellaneous_source,256);"/><br>
				<bean:message key="statusreport.action.text" />: <span style="font-size: 7pt;">(max 256)</span>
				<span style="color:red"><html:errors property="miscellaneous_action" /></span><br>
				<html:textarea property="miscellaneous_action" cols="100" rows="4" readonly="${!isReportEditable}" 
					onkeydown="limitText(this.form.miscellaneous_action,256);" 
					onkeyup="limitText(this.form.miscellaneous_action,256);"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" nowrap="nowrap">
				<html:radio property="miscellaneous_status" disabled="${!isReportEditable}"
					value="3"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/red.gif" /> <bean:message 
					key="statusreport.status.red"/></html:radio><br>
				<html:radio property="miscellaneous_status" disabled="${!isReportEditable}" 
					value="2"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/yellow.gif" /> <bean:message 
					key="statusreport.status.yellow"/></html:radio><br>			
				<html:radio property="miscellaneous_status" disabled="${!isReportEditable}"
					value="1"><html:img 
					style="width:15px; height:15px;" 
					src="/tb/images/green.gif" /> <bean:message 
					key="statusreport.status.green"/></html:radio>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- report status -->		
		<tr>
			<td class="noBborderStyle" valign="top" align="left"><b><bean:message key="statusreport.released.text" />:</b></td>
			<td class="noBborderStyle" valign="top" align="left" colspan="2"><fmt:formatDate value="${currentStatusReport.released}" pattern="yyyy-MM-dd HH:mm" /></td>
			<td class="noBborderStyle" valign="top" align="left"><c:out value="${currentStatusReport.releasedby.name}" /></td>
		</tr>
		<tr>
			<td class="noBborderStyle" valign="top" align="left"><b><bean:message key="statusreport.accepted.text" />:</b></td>
			<td class="noBborderStyle" valign="top" align="left" colspan="2"><fmt:formatDate value="${currentStatusReport.accepted}" pattern="yyyy-MM-dd HH:mm" /></td>
			<td class="noBborderStyle" valign="top" align="left"><c:out value="${currentStatusReport.acceptedby.name}" /></td>
		</tr>
						
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		
		<!-- notes -->
		<tr>
			<td class="noBborderStyle" valign="top" align="left">
				<b><bean:message key="statusreport.notes.text" />:</b>
			</td>
			<td class="noBborderStyle" valign="top" align="left" colspan="3">
				<bean:message key="statusreport.text.text" />: <span style="font-size: 7pt;">(max 2048)</span>
				<span style="color:red"><html:errors property="notes" /></span><br>
				<html:textarea property="notes" cols="100" rows="10" 
					onkeydown="limitText(this.form.notes,2048);" 
					onkeyup="limitText(this.form.notes,2048);"/>
			</td>
		</tr>
		
		<tr><td colspan="4" class="noBborderStyle"><hr></td></tr>
		
		<!-- Buttons -->
		<tr><td colspan="4" class="noBborderStyle">
			<html:submit onclick="setStoreAction(this.form, 'back')"
				styleId="button"><bean:message key="statusreport.button.back.text"/></html:submit>
			<html:submit onclick="setStoreAction(this.form, 'save')"
				styleId="button"><bean:message key="statusreport.button.save.text"/></html:submit>
			<c:if test="${isReportReadyForRelease}"><html:submit onclick="setStoreAction(this.form, 'release')"
				styleId="button"><bean:message key="statusreport.button.release.text" /></html:submit></c:if>
			<c:if test="${isReportReadyForAcceptance}"><html:submit onclick="setStoreAction(this.form, 'accept')"
				styleId="button"><bean:message key="statusreport.button.accept.text" /></html:submit></c:if>
			<c:if test="${loginEmployee.status == 'adm' && currentStatusReport != null && currentStatusReport.released != null}"><html:submit 
				onclick="setStoreAction(this.form, 'removeRelease')"
				styleId="button"><bean:message key="statusreport.button.removerelease.text" /></html:submit></c:if>	
			<br>&nbsp;
		</td></tr>
		
	</table>
</html:form>

</body>
</html>