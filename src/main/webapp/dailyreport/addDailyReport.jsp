<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<html:html>
<head>
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addtimereport.text" /></title>
<jsp:include flush="true" page="/head-includes.jsp" />
<script>
	var currentUser = "${currentEmployeeContract.employee.sign}";
</script>
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
 		form.action = "/do/StoreDailyReport?task=refreshOrders";
		form.submit();
	}

	function setToggleShowAllMinutes(form) {
		form.action = "/do/StoreDailyReport?task=toggleShowAllMinutes";
		form.submit();
	}

	function setUpdateSubordersAction(select) {	
 		var form = select.form;
		if(select.options.length>0) {
			var orderIndex = select.options[select.selectedIndex].value;
			var paramToAdd = "";
			if(orderIndex && orderIndex !== "0") {
				var suborderIndex = HBT.Salat.FavouriteOrders.getDefaultSuborder(orderIndex);
				if(suborderIndex && suborderIndex !== "0") {
					paramToAdd = "&defaultSuborderIndex=" + suborderIndex;
				}
			}

			form.action = "/do/StoreDailyReport?task=refreshSuborders" + paramToAdd;
			form.submit();
		}
	}

	function adjustSuborderSignChangedAction(form) {	
 		form.action = "/do/StoreDailyReport?task=adjustSuborderSignChanged";
		form.submit();
	}
	
	function afterCalenderClick() {
		document.forms[0].action = "/do/StoreDailyReport?task=adjustBeginTime";
		document.forms[0].submit();	
	}			
	
	function setDate(howMuch) {
		document.forms[0].action = "/do/StoreDailyReport?task=setDate&howMuch=" + howMuch;
		document.forms[0].submit();
	}

	function saveBeginOfWorkingDay(form) {
		form.action = "/do/StoreDailyReport?task=saveBeginOfWorkingDay";
		form.submit();
	}
	
	function setUpdateHoursAction(form) {
 		form.action = "/do/StoreDailyReport?task=refreshHours";
		form.submit();
	}	
	
	function setUpdatePeriodAction(form) {	
 		form.action = "/do/StoreDailyReport?task=refreshPeriod";
		form.submit();
	}	
	
	function setStoreAction(form, actionVal, addMore) {
		form.action = "/do/StoreDailyReport?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}	
			
	function backToOverview(form) {	
 		form.action = "/do/ShowDailyReport?task=refreshTimereports";
		form.submit();
	}

	function findForm(item) {
		while(item) {
			if(item.form) {
				return item.form;
			}
			if(item.tagName && item.tagName.toLowerCase() == "form") {
				return item;
			}
			item = item.parentElement;
		}
		return null;
	}

	$(document).ready(function() {
		$(".ecCls").select2({
			dropdownAutoWidth: true,
			width: 'auto'
		});	
		
		HBT.Salat.FavouriteOrders.initializeOrderSelection();
		HBT.Salat.FavouriteOrders.initializeSuborderSelection();
	});		
</script>
	<link rel="stylesheet" href="<c:url value="/webjars/bootstrap-icons/font/bootstrap-icons.min.css"/>">
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />
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
					<c:forEach var="employeecontract" items="${employeecontracts}" >
						<html:option value="${employeecontract.id}">
							<c:out value="${employeecontract.employee.name}" /> |
							<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if
								test="${employeecontract.openEnd}"><bean:message
								key="main.general.open.text" /></c:if>)
						</html:option>
					</c:forEach>
				</html:select> 
			</td>
		</tr>

		<!-- Datumsauswahl -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.timereport.referenceday.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<input type='date' name='referenceday' value='<bean:write name="addDailyReportForm" property="referenceday" />' onchange="afterCalenderClick(findForm(this))" />
				<%-- Arrows for navigating the Date --%>
				<a href="javascript:setDate('-7')" title="<bean:message key="main.date.popup.prevweek" />"><i class="bi bi-skip-backward-btn"></i></a>
				<a href="javascript:setDate('-1')" title="<bean:message key="main.date.popup.prevday" />"><i class="bi bi-skip-start-btn"></i></a>
				<a href="javascript:setDate('0')" title="<bean:message key="main.date.popup.today" />"><i class="bi bi-stop-btn"></i></a>
				<a href="javascript:setDate('1')" title="<bean:message key="main.date.popup.nextday" />"><i class="bi bi-skip-end-btn"></i></a>
				<a href="javascript:setDate('7')" title="<bean:message key="main.date.popup.nextweek" />"><i class="bi bi-skip-forward-btn"></i></a>
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
		<tr>
			<td align="left" class="noBborderStyle" nowrap="nowrap">
				<b><bean:message key="main.timereport.customerorder.text" />&nbsp;/&nbsp;<bean:message key="main.timereport.suborder.text" />:</b>
			</td>

			<td align="left" class="noBborderStyle" nowrap="nowrap"  width="90%" >
				<html:select property="orderId" onchange="setUpdateSubordersAction(this)" styleClass="make-select2 orderCls">
					<html:options collection="orders" labelProperty="signAndDescription" property="id" />
				</html:select>
				<img id="favOrderBtn" class="favOrderBtn" src="<c:url value="/images/Button/whiteStar.svg"/>" width="20" height="20" title="<bean:message key="add.report.no.default.order" />" onclick="HBT.Salat.FavouriteOrders.actionOrderSet(this);" />
				<b> / </b>
				<html:select property="suborderSignId" styleClass="mandatory make-select2 suborderCls" value="${currentSuborderId}"
					onchange="adjustSuborderSignChangedAction(this.form)">
					<html:options collection="suborders" labelProperty="signAndDescription"	property="id" />
				</html:select>
				<img id="favSuborderBtn" class="favOrderBtn" src="<c:url value="/images/Button/whiteStar.svg"/>" width="20" height="20" title="<bean:message key="add.report.no.default.suborder" />" onclick="HBT.Salat.FavouriteOrders.actionSuborderSet(this);" />
				<span style="color:red">
					<html:errors property="orderId" />
					<html:errors property="suborderId" />
				</span>
			</td>
		</tr>

		<c:if test="${dailyReportViewHelper.displayWorkingDayStartBreak}">
			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.timereport.startofwork.day.text" /></b>&nbsp;<i>(hh:mm)</i><b>:</b>
				</td>
				<td align="left" class="noBborderStyle">
					<html:select property="selectedHourBeginDay">
						<html:options collection="hours" property="value" labelProperty="label" />
					</html:select>
					<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
					<html:select property="selectedMinuteBeginDay">
						<html:options collection="minutes" property="value"	labelProperty="label" />
					</html:select>
					<a href="#" onclick="saveBeginOfWorkingDay(findForm(this))" title="save start of work"><i class="bi bi-floppy"></i></a>
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
							<c:if test="${!dailyReportViewHelper.containsMinuteOption(minutes, addDailyReportForm.selectedMinuteBegin)}">
								<html:option value="${addDailyReportForm.selectedMinuteBegin}">${addDailyReportForm.selectedMinuteBegin}</html:option>
							</c:if>
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
							<c:if test="${!dailyReportViewHelper.containsMinuteOption(minutes, addDailyReportForm.selectedMinuteEnd)}">
								<html:option value="${addDailyReportForm.selectedMinuteEnd}">${addDailyReportForm.selectedMinuteEnd}</html:option>
							</c:if>
						</html:select>
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
					<c:if test="${!dailyReportViewHelper.containsMinuteOption(minutes, addDailyReportForm.selectedMinuteDuration)}">
						<html:option value="${addDailyReportForm.selectedMinuteDuration}">${addDailyReportForm.selectedMinuteDuration}</html:option>
					</c:if>
				</html:select>
				<html:checkbox property="showAllMinutes" onchange="setToggleShowAllMinutes(this.form)" /> <bean:message key="main.timereport.showallminutes.text" />
				<span style="color:red">
					<html:errors property="selectedDuration" />
				 </span>
			</td>
		</tr>

		<c:if test="${dailyReportViewHelper.displayTraining}">
			<tr>
				<td align="left" class="noBborderStyle">
					<b><bean:message key="main.timereport.training.text"/>:</b>
				</td>
				<td align="left" class="noBborderStyle">
					<html:checkbox property="training" />
				</td>
			</tr>
		</c:if>
				
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
