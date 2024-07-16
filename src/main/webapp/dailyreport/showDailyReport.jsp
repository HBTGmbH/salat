<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
	<head>
		<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.daily.text" /></title>
		<jsp:include flush="true" page="/head-includes.jsp" />
		<script type="text/javascript" language="JavaScript">
			var failedMassEditIds = '${failedMassEditIds.toString()}';
			if(failedMassEditIds != "") {
				failedMassEditIds = JSON.parse(failedMassEditIds);
			}
		</script>
		<script type="text/javascript" language="JavaScript">


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
			function afterCalenderClick() {
				document.forms[0].action = "/do/ShowDailyReport?task=refreshTimereports";
				document.forms[0].submit();
			}

			function changeDateAndUpdateTimereportsAction(form, date, change) {
				form.action = "/do/ShowDailyReport?task=refreshTimereports&date=" + date + "&change=" + change;
				form.submit();
			}

			function setMonth(form, change) {
				form.action = "/do/ShowDailyReport?task=refreshTimereports&change=" + change;
				form.submit();
			}

			function setUpdateTimereportsAction(form) {
				form.action = "/do/ShowDailyReport?task=refreshTimereports";
				form.submit();
			}

			function switchEmployee(form) {
				form.action = "/do/ShowDailyReport?task=switchEmployee";
				form.submit();
			}

			function setUpdateOrdersAction(form) {
				form.action = "/do/ShowDailyReport?task=refreshOrders";
				form.submit();
			}

			function setUpdateSubordersAction(form, id) {
				alert('id: ' + id);
				form.action = "/do/ShowDailyReport?task=refreshSuborders&trId=" + id;
				form.submit();
			}

			function printMyFormElement(form) {
				alert('element: ' + form.elements['comment'].value);
			}

			function createNewReportAction(form) {
				form.action = "/do/CreateDailyReport";
				form.submit();
			}

			function setStoreAction(form, actionVal) {
				form.action = "/do/StoreDailyReport?task=" + actionVal;
				form.submit();
			}

			function setToggleShowAllMinutes(form) {
				form.action = "/do/ShowDailyReport?task=toggleShowAllMinutes";
				form.submit();
			}

			function confirmDelete(form, id) {
				var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
				if (agree) {
					form.action = "/do/DeleteTimereportFromDailyDisplay?trId=" + id;
					form.submit();
				}
			}

			function submitUpdateDailyReport(form, id) {
				form.action = "/do/UpdateDailyReport?trId=" + id;
				form.submit();
			}

			function confirmSave(form, id, time) {

				if(time != null) {
					var newValue=getExtendTime([form.selectedDurationHour.value, form.selectedDurationMinute.value], time)

					form.selectedDurationHour.value =newValue[0];
					form.selectedDurationMinute.value = newValue[1];
				}

				if (form.elements['status'] != null && form.elements['status'].value == 'closed') {
					var agree=confirm("<bean:message key="main.timereport.confirmclose.text" />");
					if (agree) {
						submitUpdateDailyReport(form, id);
					}
				} else {
					submitUpdateDailyReport(form, id);
				}
			}

			function saveBegin(form, time) {
				if(time != null) {
					form.selectedWorkHourBegin.value = time.getHours();
					form.selectedWorkMinuteBegin.value = roundMinutes(time.getMinutes());
				}
				form.action = "/do/ShowDailyReport?task=saveBegin";
				form.submit();
			}

			function roundMinutes(minutes) {
				return Math.round(minutes/5)*5;
			}

			function getExtendTime(currentValue, time) {
				var quittingTime= '<c:out value="${quittingtime}"></c:out>';
				var quittingTimeArray=quittingTime.split(":");
				var diff=[ time.getHours() - Number(quittingTimeArray[0]),
					       time.getMinutes()-Number(quittingTimeArray[1])];
				var res= [ Number(currentValue[0])+diff[0],
						roundMinutes(Number(currentValue[1])+diff[1])];

				if (res[1]<0) res= [res[0]-1, 60-res[1]];
				if (res[1]>=60) res= [res[0]+1, res[1]-60];
				if (res[0]<0){
					confirm("<bean:message key="main.timereport.extendTime.error.text" />");
					return currentValue;
				}
				return res;
			}

			function saveBreak(form, time) {
				if(time != null) {
					var newValue=getExtendTime([form.selectedBreakHour.value, form.selectedBreakMinute.value], time)
					if(newValue[0]>5){
						newValue=[5,55];
						confirm("<bean:message key="main.timereport.extendTime.error.text" />");
					}
					form.selectedBreakHour.value =newValue[0];
					form.selectedBreakMinute.value = newValue[1];
				}
				form.action = "/do/ShowDailyReport?task=saveBreak";
				form.submit();
			}

			function setUpdateTimereportsAction(form) {
				form.action = "/do/ShowDailyReport?task=refreshTimereports";
				form.submit();
			}

			function addFavoriteAsReport(form, date, favoriteID) {
				form.action = "/do/ShowDailyReport?task=addFavoriteAsReport&date=" + date + "&favoriteID=" + favoriteID;
				form.submit();
			}

			function createFavorite(form,timereportId) {

				form.action = "/do/ShowDailyReport?task=createFavorite&timereportId="+timereportId;
				form.submit();
			}

			function deleteFavorite(form, favoriteID) {
				form.action = "/do/ShowDailyReport?task=deleteFavorite&favoriteID=" + favoriteID;
				form.submit();
			}

			function showWMTT(Trigger,id) {
				wmtt = document.getElementById(id);
				var hint;
				hint = Trigger.getAttribute("hint");
				wmtt.style.display = "block";
			}

			function hideWMTT() {
				wmtt.style.display = "none";
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
					width: 'auto'
				});
			});

			var confirmMassDelete = '<bean:message key="main.general.confirmMassDelete.text" />';
			var cannotShiftReportsMsg = '<bean:message key="main.general.cannotShiftReports.text" />';
		</script>

		<link rel="stylesheet" href="<c:url value="/webjars/bootstrap-icons/font/bootstrap-icons.min.css"/>">
	</head>

	<body>
		<jsp:include flush="true" page="/menu.jsp">
			<jsp:param name="title" value="Menu" />
		</jsp:include>
		<br>
		<span style="font-size: 14pt; font-weight: bold;">
			<br>
			<bean:message key="main.general.mainmenu.daily.text" />
			<br>
		</span>
		<br>
		<html:form action="/ShowDailyReport">
			<table class="center backgroundcolor">
				<colgroup>
					<col align="left" width="185" />
					<col align="left" width="750" />
				</colgroup>
				<!-- select employeecontract -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.employee.fullname.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle" nowrap="nowrap">
						<html:select property="employeeContractId" value="${currentEmployeeContract.id}" onchange="switchEmployee(findForm(this))" styleClass="make-select2">
							<c:if test="${authorizedUser.manager}">
								<html:option value="-1">
									<bean:message key="main.general.allemployees.text" />
								</html:option>
							</c:if>
							<c:forEach var="employeecontract" items="${employeecontracts}">
								<html:option value="${employeecontract.id}">
									<c:out value="${employeecontract.employee.name}" /> |
									<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" />
									<c:if test="${employeecontract.openEnd}">
										<bean:message key="main.general.open.text" />
									</c:if>)
								</html:option>
							</c:forEach>
						</html:select>
					</td>
				</tr>

				<!-- select order -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.customerorder.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle" nowrap="nowrap">
						<html:select property="order" value="${currentOrder}" onchange="setUpdateTimereportsAction(findForm(this))" styleClass="make-select2">
							<html:option value="ALL ORDERS">
								<bean:message key="main.general.allorders.text" />
							</html:option>
							<html:options collection="orders" labelProperty="signAndDescription" property="sign" />
							<html:hidden property="orderId" />
						</html:select>
						<!-- select suborder -->
						<c:if test="${currentOrder != 'ALL ORDERS'}">
							<c:forEach var="order" items="${orders}">
								<c:if test="${order.sign == currentOrder}">
									/
									<html:select property="suborderId" onchange="setUpdateTimereportsAction(findForm(this))" value="${suborderFilerId}" styleClass="make-select2">
										<html:option value="-1">
											<bean:message key="main.general.allsuborders.text" />
										</html:option>
										<c:forEach var="suborder" items="${suborders}">
											<html:option value="${suborder.id}">
												<c:out value="${suborder.signAndDescription}"></c:out>
												<c:if test="${!suborder.currentlyValid}">
													&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
												</c:if>
											</html:option>
										</c:forEach>
									</html:select>
								</c:if>
							</c:forEach>
							<span style="font-size: 0.6em">
								<bean:message key="main.general.select.expired.text" />
							</span>
							<html:checkbox property="showOnlyValid" onclick="setUpdateTimereportsAction(findForm(this))" styleClass="middle-aligned">
								<span class="middle-aligned"><bean:message key="main.general.show.only.valid.text"/></span>
							</html:checkbox>
						</c:if>
						<c:if test="${currentOrder == 'ALL ORDERS'}">
							<html:checkbox property="showOnlyValid" onclick="setUpdateTimereportsAction(findForm(this))" style="display:none;" />
						</c:if>
					</td>
				</tr>

				<!-- select view mode -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.view.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="view" onchange="setUpdateTimereportsAction(findForm(this))" styleClass="make-select2">
							<html:option value="day">
								<bean:message key="main.general.timereport.view.daily.text" />
							</html:option>
							<html:option value="month">
								<bean:message key="main.general.timereport.view.monthly.text" />
							</html:option>
							<html:option value="custom">
								<bean:message key="main.general.timereport.view.custom.text" />
							</html:option>
							<html:hidden property="view" />
						</html:select>
					</td>
				</tr>

				<!-- select first date -->
				<tr>
					<c:choose>
						<c:when test="${view eq 'month'}">
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.monthyear.text" />:</b>
							</td>
						</c:when>
						<c:otherwise>
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.daymonthyear.text" /></b><c:if test="${view eq 'custom'}">&nbsp;<i>(<bean:message key="main.general.from.text" />)</i></c:if><b>:</b>
							</td>
						</c:otherwise>
					</c:choose>

					<td align="left" class="noBborderStyle">
						<c:choose>
							<c:when test="${!(view eq 'month')}">
								<input type='date' name='startdate' value='<bean:write name="showDailyReportForm" property="startdate" />' onchange="afterCalenderClick(findForm(this))" />
								<%-- Arrows for navigating the Date --%>
								<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'start','-7'); return false" class="mr2" title="<bean:message key="main.date.popup.prevweek" />"><i class="bi bi-skip-backward-btn"></i></a>
								<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'start','-1'); return false" class="mr2" title="<bean:message key="main.date.popup.prevday" />"><i class="bi bi-skip-start-btn"></i></a>
								<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'start','0'); return false" class="mr2" title="<bean:message key="main.date.popup.today" />"><i class="bi bi-stop-btn"></i></a>
								<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'start','1'); return false" class="mr2" title="<bean:message key="main.date.popup.nextday" />"><i class="bi bi-skip-end-btn"></i></a>
								<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'start','7'); return false" class="mr2" title="<bean:message key="main.date.popup.nextweek" />"><i class="bi bi-skip-forward-btn"></i></a>
							</c:when>
							<c:otherwise>
								<html:select property="month" onchange="setUpdateTimereportsAction(findForm(this))" styleClass="make-select2">
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
								</html:select>
								<html:select property="year" value="<%=(String) request.getSession().getAttribute(\"currentYear\")%>"
									onchange="setUpdateTimereportsAction(findForm(this))" styleClass="make-select2">
									<html:options collection="years" property="value" labelProperty="label" />
								</html:select>
								<br />
								<%-- Arrows for navigating the month --%>
								<a href="#" onclick="setMonth(findForm(this),'-12'); return false"><i class="bi bi-skip-backward-btn"></i></a>
								<a href="#" onclick="setMonth(findForm(this),'-1'); return false"><i class="bi bi-skip-start-btn"></i></a>
								<a href="#" onclick="setMonth(findForm(this),'0'); return false"><i class="bi bi-stop-btn"></i></a>
								<a href="#" onclick="setMonth(findForm(this),'1'); return false"><i class="bi bi-skip-end-btn"></i></a>
								<a href="#" onclick="setMonth(findForm(this),'12'); return false"><i class="bi bi-skip-forward-btn"></i></a>
							</c:otherwise>
						</c:choose>
					</td>
				</tr>

				<!-- select second date -->
				<c:if test="${view eq 'custom'}">
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.monthlyreport.daymonthyear.text" /></b>&nbsp;<i>(<bean:message key="main.general.to.text" />)</i><b>:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<input type='date' name='enddate' value='<bean:write name="showDailyReportForm" property="enddate" />' onchange="afterCalenderClick(findForm(this))" />
							<%-- Arrows for navigating the Date --%>
							<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'end','-7')" title="<bean:message key="main.date.popup.prevweek" />"><i class="bi bi-skip-backward-btn"></i></a>
							<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'end','-1')" title="<bean:message key="main.date.popup.prevday" />"><i class="bi bi-skip-start-btn"></i></a>
							<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'end','0')" title="<bean:message key="main.date.popup.today" />"><i class="bi bi-stop-btn"></i></a>
							<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'end','1')" title="<bean:message key="main.date.popup.nextday" />"><i class="bi bi-skip-end-btn"></i></a>
							<a href="#" onclick="changeDateAndUpdateTimereportsAction(document.forms.showDailyReportForm,'end','7')" title="<bean:message key="main.date.popup.nextweek" />"><i class="bi bi-skip-forward-btn"></i></a>
						</td>
					</tr>
				</c:if>

				<!-- avoid refresh -->
				<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.avoidrefresh.text"/>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="avoidRefresh" onclick="setUpdateTimereportsAction(findForm(this))" />
					</td>
				</tr>

				<!-- show only project based training -->
				<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.showOnlyTraining.text"/>:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="showTraining" onclick="setUpdateTimereportsAction(findForm(this))" />
					</td>
				</tr>

				<!-- compute overtime until chosen Date -->
	      		<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.overtimeUntilDate"/>:</b>
					</td>
					<c:choose>
	      				<c:when test="${overtimeDisabled=='true'}">
							<td align="left" class="noBborderStyle">
								<html:checkbox property="showOvertimeUntil" onclick="setUpdateTimereportsAction(findForm(this))" disabled="true"/>
							</td>
						</c:when>
						<c:otherwise>
							<td align="left" class="noBborderStyle">
								<html:checkbox property="showOvertimeUntil" onclick="setUpdateTimereportsAction(findForm(this))" />
							</td>
						</c:otherwise>
					</c:choose>
				</tr>

				<!-- toggle full minutes -->
				<tr>
					<td align="left" valign="top" class="noBborderStyle">
						<b><bean:message key="main.timereport.showallminutes.text" /></b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="showAllMinutes" onchange="setToggleShowAllMinutes(findForm(this))" />
					</td>
				</tr>

				<!-- seperator line -->
				<tr>
					<td width="100%" class="noBborderStyle" colspan="2">
						<hr>
					</td>
				</tr>

				<!-- select working day begin and  break -->
				<c:if test="${view eq 'day' || view == null}">
					<c:if test="${currentEmployee != 'ALL EMPLOYEES'}">
						<tr>
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.timereport.startofwork.text" /></b> <i>(hh:mm)</i><b>:</b>
							</td>
							<td align="left" class="noBborderStyle">
								<nobr>
									<html:select property="selectedWorkHourBegin" styleClass="make-select2">
										<html:options collection="hours" property="value" labelProperty="label" />
									</html:select>
									<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
									<html:select property="selectedWorkMinuteBegin" styleClass="make-select2">
										<html:options collection="minutes" property="value" labelProperty="label" />
									</html:select>

									<a href="#" onclick="saveBegin(findForm(this))" title="save start of work"><i class="bi bi-floppy"></i></a>
									<a href="#" onclick="saveBegin(findForm(this),new Date())" title="save start of work"><i class="bi bi-watch"></i></a>
								</nobr>
							</td>
						</tr>

						<%-- is a visible, when workingday null --%>
						<c:if test="${visibleworkingday}">
							<tr>
								<td align="left" class="noBborderStyle">
									<b><bean:message key="main.timereport.breakduration.text" /></b> <i>(hh:mm)</i><b>:</b>
								</td>
								<td align="left" class="noBborderStyle">
									<html:select property="selectedBreakHour" styleClass="make-select2">
										<html:options collection="breakhours" property="value" labelProperty="label" />
									</html:select>
									<b>&nbsp;&nbsp;:&nbsp;&nbsp;</b>
									<html:select property="selectedBreakMinute" styleClass="make-select2">
										<html:options collection="breakminutes" property="value" labelProperty="label" />
									</html:select>

									<a href="#" onclick="saveBreak(findForm(this))" title="save break"><i class="bi bi-floppy"></i></a>
									<a href="#" onclick="saveBreak(findForm(this),new Date())" title="save break"><i class="bi bi-skip-end"></i></a>
								</td>
							</tr>
							<tr>
								<td align="left" class="noBborderStyle">
									<b><bean:message key="main.timereport.quittingtime.text" />:</b>
								</td>
								<td align="left" class="noBborderStyle">
									<b><c:out value="${quittingtime}"></c:out></b>
								</td>
							</tr>
							<tr>
								<td align="left" class="noBborderStyle">
									<b><bean:message key="main.timereport.workingdayends.text" />:</b>
								</td>
								<td align="left" class="noBborderStyle">
									<b><c:out value="${workingDayEnds}"></c:out></b>
								</td>
							</tr>
						</c:if>
					</c:if>
				</c:if>
			</table>
		</html:form>

		<bean:size id="timereportsSize" name="timereports" />
		<c:if test="${timereportsSize>10}">
			<table>
				<tr>
					<html:form action="/CreateDailyReport">
						<td class="noBborderStyle" colspan="6" align="left">
							<html:submit styleId="button" titleKey="main.general.button.createnewreport.alttext.text">
								<bean:message key="main.general.button.createnewreport.text" />
							</html:submit>
						</td>
					</html:form>
					<html:form target="_blank" action="/ShowDailyReport?task=print">
						<td class="noBborderStyle" colspan="6" align="left">
							<html:submit styleId="button" titleKey="main.general.button.printpreview.alttext.text">
								<bean:message key="main.general.button.printpreview.text" />
							</html:submit>
						</td>
					</html:form>
				</tr>
			</table>
		</c:if>

		<c:if test="${vacationBudgetOverrun}">
			<table>
				<td  class="noBborderStyle"  style="font-size: 14pt; ">
					<b><font color="red"><bean:message key="form.timereport.error.vacationBudgetOverrun" /></font></b>
				</td>
			</table>
		</c:if>

		<html:form action="/ShowDailyReport">
			<div class="favorites">
				<c:forEach var="favorite" items="${favorites}" varStatus="statusID">
					<div class="favorite-container">
						<div class="button favorite"
								onclick="addFavoriteAsReport(findForm(this),'<bean:write name="showDailyReportForm" property="enddate"/>', ${favorite.id}); return false"
								title="Erstellen"
								>
							${favorite.comment} ${favorite.hours}:${favorite.minutes}
						</div>
						<div class="button favoriteDelete"
							 onclick="deleteFavorite(findForm(this), ${favorite.id}); return false" title="Löschen" class="delete"
							 title="delete">
							<i class="bi bi-trash"></i>
						</div>
					</div>
				</c:forEach>
			</div>
		</html:form>

		<table class="center backgroundcolor nobBorderStyle" width="100%">
			<tr class="noBborderStyle">
				<td colspan="5" class="noBborderStyle">
					&nbsp;
				</td>
				<td colspan="2" class="noBborderStyle" align="right">
					<b><bean:message key="main.timereport.total.text" />:</b>
				</td>
				<c:choose>
					<c:when test="${maxlabortime&& view eq 'day' && !(currentEmployee eq 'ALL EMPLOYEES')}">
						<th class="noBborderStyle" align="center" color="red" >
							<b><font color="red"><c:out	value="${labortime}"></c:out></font></b>
						</th>
					</c:when>
					<c:otherwise>
						<th class="noBborderStyle" align="center">
							<b><c:out value="${labortime}"></c:out></b>
						</th>
					</c:otherwise>
				</c:choose>
			</tr>

			<tr class="noBborderStyle">
				<th class="noBborderStyle" align="left">
					<b>Info</b>
				</th>
				<th class="noBborderStyle employee-col" align="left" title="<bean:message key='main.headlinedescription.dailyoverview.employee.text' />">
					<html:link href="/do/ShowDailyReport?task=sort&column=employee">
						<b><bean:message key="main.timereport.monthly.employee.sign.text" /></b>
					</html:link>
					<c:if test="${timereportSortColumn eq 'employee'}">
						<c:out value="${timereportSortModus}" />
					</c:if>
				</th>
				<th class="noBborderStyle" align="left" title="<bean:message key='main.headlinedescription.dailyoverview.refday.text' />">
					<html:link href="/do/ShowDailyReport?task=sort&column=refday">
						<b><bean:message key="main.timereport.monthly.refday.text" /></b>
					</html:link>
					<c:if test="${timereportSortColumn eq 'refday'}">
						<c:out value="${timereportSortModus}" />
					</c:if>
				</th>
				<th class="noBborderStyle" align="left" title="<bean:message key='main.headlinedescription.dailyoverview.customerorder.text' />">
					<html:link href="/do/ShowDailyReport?task=sort&column=order">
						<b><bean:message key="main.timereport.monthly.customerorder.text" /></b>
					</html:link>
					<c:if test="${timereportSortColumn eq 'order'}">
						<c:out value="${timereportSortModus}" />
					</c:if>
				</th>
				<th class="noBborderStyle" align="left" title="<bean:message key='main.headlinedescription.dailyoverview.description.text' />">
					<b><bean:message key="main.customerorder.shortdescription.text" /></b>
				</th>
				<th class="noBborderStyle" align="left" title="<bean:message key='main.headlinedescription.dailyoverview.taskdescription.text' />" width="25%">
					<b><bean:message key="main.timereport.monthly.taskdescription.text" /></b>
				</th>
				<th class="noBborderStyle training-col" align="center" title="<bean:message key='main.headlinedescription.dailyoverview.training.text' />">
					<b><bean:message key="main.timereport.monthly.training.text" /></b>
				</th>
				<th class="noBborderStyle" align="center" title="<bean:message key='main.headlinedescription.dailyoverview.hours.text' />">
					<b><bean:message key="main.timereport.monthly.hours.text" /></b>
				</th>
				<th class="noBborderStyle" align="center" title="<bean:message	key='main.headlinedescription.dailyoverview.saveeditdelete.text' />">
					<b><bean:message key="main.timereport.monthly.saveeditdelete.text" /></b>
				</th>
			</tr>

			<c:forEach var="timereport" items="${timereports}" varStatus="statusID">
				<html:form action="/UpdateDailyReport?trId=${timereport.id}">
					<c:choose>
						<c:when test="${statusID.count%2 == 0}">
							<tr class="noBborderStyle primarycolor">
						</c:when>
						<c:otherwise>
							<tr class="noBborderStyle secondarycolor">
						</c:otherwise>
					</c:choose>

					<!-- Info -->
					<td class="noBborderStyle" align="center">
						<div class="tooltip-parent">
							<div class="tooltip" id="info<c:out value='${timereport.id}'/>">
								<table>
									<tr>
										<td class="info">id:</td>
										<td class="info" colspan="3">
											<c:out value="${timereport.id}" />
										</td>
									</tr>
									<tr>
										<td class="info"><bean:message key="main.timereport.tooltip.employee" />:</td>
										<td class="info" colspan="3">
											<c:out value="${timereport.employeeName}" />
										</td>
									</tr>
									<tr>
										<td class="info"><bean:message key="main.timereport.tooltip.order" />:</td>
										<td class="info" colspan="3">
											<c:out	value="${timereport.customerorderSign}" />
										</td>
									</tr>
									<tr>
										<td class="info">&nbsp;</td>
										<td class="info" colspan="3">
											<c:out	value="${timereport.customerorderDescription}" />
										</td>
									</tr>
									<tr>
										<td class="info"><bean:message key="main.timereport.tooltip.suborder" />:</td>
										<td class="info" colspan="3">
											<c:out value="${timereport.suborderSign}" />
										</td>
									</tr>
									<tr>
										<td class="info">&nbsp;</td>
										<td class="info" colspan="3">
											<c:out value="${timereport.suborderDescription}" />
										</td>
									</tr>
									<tr>
										<td class="info"><bean:message key="main.timereport.tooltip.status" />:</td>
										<td class="info">
											<c:out value="${timereport.status}" />
										</td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message key="main.timereport.tooltip.created" />:</td>
										<td class="info">
											<java8:formatLocalDateTime value="${timereport.created}" />
										</td>
										<td class="info" valign="top"><bean:message key="main.timereport.tooltip.by" /></td>
										<td class="info" valign="top">
											<c:out value="${timereport.createdby}" />
										</td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message key="main.timereport.tooltip.edited" />:</td>
										<td class="info">
											<java8:formatLocalDateTime value="${timereport.lastupdate}" />
										</td>
										<td class="info" valign="top"><bean:message key="main.timereport.tooltip.by" /></td>
										<td class="info" valign="top">
											<c:out value="${timereport.lastupdatedby}" />
										</td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message	key="main.timereport.tooltip.released" />:</td>
										<td class="info">
											<c:out value="${timereport.released}" />
										</td>
										<td class="info" valign="top"><bean:message key="main.timereport.tooltip.by" /></td>
										<td class="info" valign="top">
											<c:out value="${timereport.releasedby}" />
										</td>
									</tr>
									<tr>
										<td class="info" valign="top"><bean:message	key="main.timereport.tooltip.accepted" />:</td>
										<td class="info">
											<c:out value="${timereport.accepted}" />
										</td>
										<td class="info" valign="top"><bean:message	key="main.timereport.tooltip.by" /></td>
										<td class="info" valign="top">
											<c:out value="${timereport.acceptedby}" />
										</td>
									</tr>
								</table>
							</div>
							<i class="bi bi-info-circle"></i>
						</div>
						<c:if test="${!timereport.fitsToContract}">
							<img width="20px" height="20px" src="<c:url value="/images/Pin%20rot.gif"/>" title="<bean:message key='main.timereport.warning.datedoesnotfit' />" />
						</c:if>
					</td>

					<!-- Mitarbeiter -->
					<td class="noBborderStyle">
						<c:out value="${timereport.employeeSign}" />
					</td>

					<!-- Datum -->
					<td class="noBborderStyle">
						<logic:equal name="timereport" property="holiday" value="true">
							<span style="color: red">
								<java8:formatLocalDate value="${timereport.referenceday}" />
							</span>
						</logic:equal>
						<logic:equal name="timereport" property="holiday" value="false">
							<java8:formatLocalDate value="${timereport.referenceday}" />
						</logic:equal>
					</td>
					<!-- Auftrag -->
					<td class="noBborderStyle">
						<c:out value="${timereport.customerorderSign}" />
						<br>
						<c:out value="${timereport.suborderSign}" />
					</td>

					<!-- Bezeichnung -->
					<td class="noBborderStyle">
						<c:out value="${timereport.customerorderDescription}" />
						<br>
						<c:out value="${timereport.suborderDescription}" />
					</td>

					<!-- visibility dependent on user and status -->
					<c:choose>
						<c:when	test="${((loginEmployee.id == timereport.employeeId) && (timereport.status eq 'open')) || (authorizedUser.manager && timereport.status eq 'commited' && loginEmployee.id != timereport.employeeId) || authorizedUser.admin}">
							<!-- Kommentar -->
							<td class="noBborderStyle">
								<html:textarea property="comment" cols="30" rows="1" value="${timereport.taskdescription}"
									onkeydown="limitText(this.form.comment,256);"
									onkeyup="limitText(this.form.comment,256);"
									styleClass="showDailyReport" />
							</td>

							<!-- Fortbildung -->
							<td class="noBborderStyle" align="center">
								<input type="checkbox" name="training" ${timereport.training ? 'checked' : '' } />
							</td>

							<!-- Dauer -->
							<td class="noBborderStyle report-time" align="center" nowrap="nowrap">
								<html:select name="timereport" property="selectedDurationHour" value="${timereport.durationhours}" disabled="${timereport.suborderSign eq overtimeCompensation}" styleClass="make-select2">
									<html:options collection="hoursDuration" property="value" labelProperty="label" />
								</html:select>
								<html:select property="selectedDurationMinute" value="${timereport.durationminutes}" disabled="${timereport.suborderSign eq overtimeCompensation}" styleClass="make-select2">
									<html:options collection="minutes" property="value"	labelProperty="label" />
									<c:if test="${!dailyReportViewHelper.containsMinuteOption(minutes, timereport.durationminutes)}">
										<html:option value="${timereport.durationminutes}">${timereport.durationminutes}</html:option>
									</c:if>
								</html:select>
							</td>

							<!-- Bearbeiten -->
							<td class="noBborderStyle report-options" align="center">

								<button type="button"  onclick="confirmSave(findForm(this), ${timereport.id}, new Date()); return false" title="Speichern" style="border: 0; background-color: transparent"><i class="bi bi-skip-end"></i></button>
								<button type="button"  onclick="confirmSave(findForm(this), ${timereport.id}); return false" title="Speichern" style="border: 0; background-color: transparent"><i class="bi bi-floppy"></i></button>

								<button type="button"  onclick="createFavorite(findForm(this), ${timereport.id}); return false"
										title="Favorite" style="border: 0; background-color: transparent"><i class="bi bi-star"></i></button>
								<a href="/do/EditDailyReport?trId=${timereport.id}" title="Ändern"><i class="bi bi bi-pencil"></i></a>

								<button onclick="confirmDelete(findForm(this), ${timereport.id}); return false" title="Löschen" style="border: 0; background-color: transparent"><i class="bi bi bi-trash"></i></button>
								<span id="span-massedit-${timereport.id}">
									<input type="checkbox" class="massedit" title='<bean:message key="main.timereport.tooltip.mass.edit" />' alt='<bean:message key="main.timereport.tooltip.mass.edit" />' id="massedit_${timereport.id}" onchange="HBT.MassEdit.onChangeHandler(this)" />
								</span>
							</td>
						</c:when>
						<c:otherwise>
							<!-- Kommentar -->
							<td class="noBborderStyle">
								<c:choose>
									<c:when test="${timereport.taskdescription eq ''}">
										&nbsp;
									</c:when>
									<c:otherwise>
										<c:out value="${timereport.taskdescription}" />
									</c:otherwise>
								</c:choose>
							</td>
							<!-- Fortbildung -->
							<td class="noBborderStyle" align="center">
								<input type="checkbox" name="training" ${timereport.training ? 'checked' : '' } />
							</td>
							<!-- Dauer -->
							<td class="noBborderStyle" align="center" nowrap>
								<java8:formatDuration value="${timereport.duration}"/>
							</td>
							<!-- Bearbeiten -->
							<td class="noBborderStyle" align="center">
								<img width="12px" height="12px" src="<c:url value="/images/verbot.gif"/>" alt="Delete Timereport" />
							</td>
						</c:otherwise>
					</c:choose>
					</tr>
				</html:form>
			</c:forEach>
			<tr class="borderTopLine">
				<td colspan="5" class="noBborderStyle">
					&nbsp;
				</td>
				<td class="noBborderStyle" colspan="2" align="right">
					<b><bean:message key="main.timereport.total.text" />:</b>
				</td>
				<c:choose>
					<c:when test="${maxlabortime && view eq 'day' && !(currentEmployee eq 'ALL EMPLOYEES')}">
						<th class="noBborderStyle" align="center" color="red">
							<b><font color="red"><c:out	value="${labortime}"></c:out></font></b>
						</th>
					</c:when>
					<c:otherwise>
						<th class="noBborderStyle" align="center">
							<b><c:out value="${labortime}"></c:out></b>
						</th>
					</c:otherwise>
				</c:choose>
				<td class="massedit invisible noBborderStyle" align="center">
					<html:form action="/ShowDailyReport" style="margin-bottom:0">
						<b><bean:message key="main.timereport.mass.edit.text" />:</b><br />
						<a href="#" onclick="return HBT.MassEdit.confirmDelete(this, confirmMassDelete);" title="Löschen">
							<i class="bi bi-trash"></i>
						</a>
						&nbsp;&nbsp;
						<div class="massedit-time-shift-dropdown">
							<a href="#" onclick="$('.dropdown-content').focus()"
							   title="Um Tage Verschieben">">
								<i class="bi bi-arrow-left-right"></i>
							</a>
							<div class="dropdown-content" tabindex="1">
								<table class="noBborderStyle">
									<tr><th colspan="2"><bean:message key="main.timereport.mass.edit.shift.days.text" /></th></tr>
									<c:forEach begin="1" end="7" varStatus="loop">
										<tr><td><span title='<bean:message key="main.timereport.mass.edit.shift.days.tooltip" arg0='-${loop.index}' />' onclick='return HBT.MassEdit.confirmShiftDays(this, "<bean:message key="main.timereport.mass.edit.shift.days.confirm.msg" arg0='-${loop.index}' />", -${loop.index});'>-${loop.index}</span></td>
										    <td><span title='<bean:message key="main.timereport.mass.edit.shift.days.tooltip" arg0='+${loop.index}' />' onclick='return HBT.MassEdit.confirmShiftDays(this, "<bean:message key="main.timereport.mass.edit.shift.days.confirm.msg" arg0='+${loop.index}' />", ${loop.index});'>+${loop.index}</span></td></tr>
									</c:forEach>
								</table>
							</div>
						</div>
					</html:form>
				</td>
			</tr>
		</table>
		<table>
			<tr>
				<html:form action="/CreateDailyReport">
					<td class="noBborderStyle" colspan="6" align="left">
						<html:submit styleId="button" titleKey="main.general.button.createnewreport.alttext.text">
							<bean:message key="main.general.button.createnewreport.text" />
						</html:submit>
					</td>
				</html:form>
				<html:form target="_blank" action="/ShowDailyReport?task=print">
					<td class="noBborderStyle" colspan="6" align="left">
						<html:submit styleId="button" titleKey="main.general.button.printpreview.alttext.text">
							<bean:message key="main.general.button.printpreview.text" />
						</html:submit>
					</td>
				</html:form>
			</tr>
		</table>
		<br>

		<span style="color: red">
			<b>
				<html:errors property="trSuborderId" footer="<br>" />
				<html:errors property="selectedHourEnd" footer="<br>" />
				<html:errors property="status" footer="<br>" />
				<br>
				<html:errors property="comment" footer="<br>" />
			</b>
		</span>

		<!-- Überstunden und Urlaubstage -->
		<c:choose>
			<c:when test="${currentEmployee != 'ALL EMPLOYEES'}">
				<br><br><br>
				<jsp:include flush="true" page="/info2.jsp">
					<jsp:param name="info" value="Info" />
				</jsp:include>
			</c:when>
			<c:otherwise>
				<br><br><br><br><br><br><br><br><br><br><br>
			</c:otherwise>
		</c:choose>
	</body>
</html:html>
