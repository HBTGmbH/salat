<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html>
	<head>
		<title>
			<bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.invoice.title.text" />
		</title>
		<jsp:include flush="true" page="/head-includes.jsp" />
		<script type="text/javascript" language="JavaScript">	
		 	function setUpdateInvoiceAction(form) {	
		 		form.action = "/do/ShowInvoice?task=refreshInvoiceForm";
				form.submit();
			}
			function exportExcel(form) {
				form.action = "/do/ShowInvoice?task=export";
				form.submit();
			}
			function exportExcelNew(form) {
				form.action = "/do/ShowInvoice?task=exportNew";
				form.submit();
			}
			function showPrint(form) {
				form.action = "/do/ShowInvoice?task=print";
				form.submit();
			}

			$(document).ready(function() {
				$(".make-select2").select2({
					dropdownAutoWidth: true,
					width: 'auto'
				});	
			});		
		</script>
	</head>
	<body>
		<jsp:include flush="true" page="/menu.jsp">
			<jsp:param name="title" value="Menu" />
		</jsp:include>
		<br>
		<span style="font-size: 14pt; font-weight: bold;">
			<br>
			<bean:message key="main.general.mainmenu.invoice.title.text" />
			<br>
		</span>
		<br>
		<html:form action="/ShowInvoice?task=generateMaximumView">
			<table class="center backgroundcolor" style="float: left; margin-right: 20px">
				<!-- dataset options title -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.dataset.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle"></td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle">
						&nbsp;
					</td>
					<td align="left" class="noBborderStyle">
						&nbsp;
					</td>
				</tr>
				<tr>
					<c:if test="${!(empty errorMessage)}">
						<td align="left" class="noBborderStyle">
							<c:out value="${errorMessage}" />
						</td>
					</c:if>
				</tr>
				<!-- select order -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.customerorder.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="order" styleClass="make-select2"
							value="<%=(String) request.getSession().getAttribute(\"currentOrder\")%>"
							onchange="setUpdateInvoiceAction(this.form)">

							<html:option value="CHOOSE ORDER">
								<bean:message key="main.invoice.choose.text" />
							</html:option>

							<html:options collection="orders"
								labelProperty="signAndDescription" property="sign" />

						</html:select>
					</td>
				</tr>

				<!-- select suborder -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.suborder.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="suborder" value="<%=(String) request.getSession().getAttribute(\"currentSuborder\")%>" onchange="setUpdateInvoiceAction(this.form)" styleClass="make-select2">
							<html:option value="ALL SUBORDERS">
								<bean:message key="main.general.allsuborders.text" />
							</html:option>
							<c:forEach var="suborder" items="${suborders}">
								<html:option value="${suborder.id}">
									<c:out value="${suborder.completeOrderSignAndDescription}" />
									&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(
									<c:out value="${suborder.timeString}" />
									<c:if test="${suborder.openEnd}">
										<bean:message key="main.general.open.text" />
									</c:if>)
								</html:option>
							</c:forEach>
						</html:select>
						<html:checkbox property="showOnlyValid" onchange="setUpdateInvoiceAction(this.form)" styleClass="middle-aligned">
							<span class="middle"><bean:message key="main.general.show.only.valid.text"/></span>
						</html:checkbox>
					</td>
				</tr>

				<!-- select view mode -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.view.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="invoiceview"
							onchange="setUpdateInvoiceAction(this.form)">
							<html:option value="month">
								<bean:message key="main.general.timereport.view.monthly.text" />
							</html:option>
							<html:option value="week">
								<bean:message key="main.general.timereport.view.weekly.text" />
							</html:option>
							<html:option value="custom">
								<bean:message key="main.general.timereport.view.custom.text" />
							</html:option>
						</html:select>
					</td>
				</tr>

				<!-- select first date -->
				<tr>
					<c:choose>
						<c:when test="${invoiceview eq 'month'}">
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.monthyear.text" />:</b>
							</td>
						</c:when>
						<c:when test="${invoiceview eq 'week'}">
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.weekyear.text" />:</b>
							</td>
						</c:when>
						<c:otherwise>
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.daymonthyear.text" />:</b>
							</td>
						</c:otherwise>
					</c:choose>

					<td align="left" class="noBborderStyle">
					
						<c:if test="${(invoiceview eq 'custom')}">
							<html:select property="fromDay" value="${currentDay}"
								onchange="setUpdateInvoiceAction(this.form)">
								<html:options collection="days" property="value"
									labelProperty="label" />
							</html:select>
						</c:if>
						<c:if test="${!(invoiceview eq 'week')}">
							<html:select property="fromMonth" value="${currentMonth}"
								onchange="setUpdateInvoiceAction(this.form)">
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
							<html:select property="fromYear" value="${currentYear}"
								onchange="setUpdateInvoiceAction(this.form)">
								<html:options collection="years" property="value"
									labelProperty="label" />
							</html:select>
						</c:if>
						<c:if test="${invoiceview eq 'week'}">
							<html:select property="fromWeek" value="${currentWeek}" onchange="setUpdateInvoiceAction(this.form)" styleClass="make-select2">
								<html:options collection="weeks" property="value" labelProperty="label" />
							</html:select>
							<html:select property="fromYear" value="${currentYear}"
								onchange="setUpdateInvoiceAction(this.form)">
								<html:options collection="years" property="value"
									labelProperty="label" />
							</html:select>
						</c:if>
					</td>
				</tr>

				<!-- select second date -->
				<c:if test="${invoiceview eq 'custom'}">
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.monthlyreport.daymonthyear.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:select property="untilDay" value="${lastDay}"
								onchange="setUpdateInvoiceAction(this.form)">
								<html:options collection="days" property="value"
									labelProperty="label" />
							</html:select>
							<html:select property="untilMonth" value="${lastMonth}"
								onchange="setUpdateInvoiceAction(this.form)">
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
							<html:select property="untilYear" value="${lastYear}"
								onchange="setUpdateInvoiceAction(this.form)">
								<html:options collection="years" property="value"
									labelProperty="label" />
							</html:select>
						</td>
					</tr>
				</c:if>

				<!-- show subordercomment -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.suborderdescription.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="suborderdescription"
							value="${optionsuborderdescription}"
							onchange="setUpdateInvoiceAction(this.form)">
							<html:option value="longdescription">
								<bean:message key="main.invoice.suborderdescription.long.text" />
							</html:option>
							<html:option value="shortdescription">
								<bean:message key="main.invoice.suborderdescription.short.text" />
							</html:option>
						</html:select>
					</td>
				</tr>
				<!-- show invoice -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.invoice.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="invoicebox"
							onchange="setUpdateInvoiceAction(this.form)" />
					</td>
				</tr>
				<!-- show fixed price offers -->
				<tr>
					<td align="left" class="noBborderStyle">
							<b><bean:message key="main.invoice.fixedprice.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:checkbox property="fixedpricebox"
								onchange="setUpdateInvoiceAction(this.form)" />
					</td>
				</tr>
				<tr>
					<td class="noBborderStyle" align="left">
						<html:submit styleId="button"
							titleKey="main.invoice.button.createmaximumview.alttext.text">
							<bean:message key="main.invoice.button.createmaximumview.text" />
						</html:submit>
					</td>
					<td class="noBborderStyle">
						&nbsp;
					</td>
				</tr>
			</table>
			<table class="center backgroundcolor">
				<!-- show/hide options title -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.showhide.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle"></td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle">
						&nbsp;
					</td>
					<td align="left" class="noBborderStyle">
						&nbsp;
					</td>
				</tr>
				<!-- limit layer -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.layerlimit.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="layerlimit"
									 value="${layerlimit}"
									 onchange="setUpdateInvoiceAction(this.form)">
							<html:option value="-1">
								<bean:message key="main.invoice.layerlimit.nolimit.text" />
							</html:option>
							<html:option value="0">
								1 <bean:message key="main.invoice.layerlimit.layer.text" />
							</html:option>
							<html:option value="1">
								1 <bean:message key="main.invoice.layerlimit.sublayer.text" />
							</html:option>
							<html:option value="2">
								2 <bean:message key="main.invoice.layerlimit.sublayers.text" />
							</html:option>
							<html:option value="3">
								3 <bean:message key="main.invoice.layerlimit.sublayers.text" />
							</html:option>
							<html:option value="4">
								4 <bean:message key="main.invoice.layerlimit.sublayers.text" />
							</html:option>
							<html:option value="5">
								5 <bean:message key="main.invoice.layerlimit.sublayers.text" />
							</html:option>
						</html:select>
					</td>
				</tr>
				<!-- show timereports -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.timereports.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="timereportsbox"
									   onchange="setUpdateInvoiceAction(this.form)" />
					</td>
				</tr>
				<c:if test="${showInvoiceForm.timereportsbox}">
					<!-- show timereport description -->
					<tr>
						<td align="left" class="noBborderStyle">
							<b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<bean:message
									key="main.invoice.timreportdescription.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:checkbox property="timereportdescriptionbox"
										   onchange="setUpdateInvoiceAction(this.form)" />
						</td>
					</tr>

					<!-- show employee signs -->
					<tr>
						<td align="left" class="noBborderStyle">
							<b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<bean:message
									key="main.invoice.employeesign.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:checkbox property="employeesignbox"
										   onchange="setUpdateInvoiceAction(this.form)" />
						</td>
					</tr>
				</c:if>
				<!-- show customer orderdescription -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.customersign.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="customeridbox"
									   onchange="setUpdateInvoiceAction(this.form)" />
					</td>
				</tr>

				<!-- show targethours -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.targethours.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="targethoursbox"
									   onchange="setUpdateInvoiceAction(this.form)" />
					</td>
				</tr>

				<!-- show actualhours -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.actualhours.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<input type="checkbox" checked="checked" disabled="disabled" />
						<html:hidden property="actualhoursbox" />
					</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle">
						&nbsp;
					</td>
					<td align="left" class="noBborderStyle">
						&nbsp;
					</td>
				</tr>
				<!-- select value added tax -->
					<%--
                    <tr>
                        <td align="left" class="noBborderStyle"><b><bean:message
                            key="main.invoice.mwst.text" />:</b></td>
                        <td align="left" class="noBborderStyle"><html:text
                            property="mwst" value="${optionmwst}" size="2" maxlength="2"
                            onchange="setUpdateInvoiceAction(this.form)" /></td>
                    </tr>
                     --%>

			</table>
		</html:form>
		<c:if test="${! empty viewhelpers}">
			<html:form target="_blank" action="/ShowInvoice?task=print" style="clear: left">
				<table>
					<tr>
						<td class="noBborderStyle" align="left">
							<i>Hinweis: Bearbeiten der Anzeigeoptionen setzt die Änderung der Adresse zurück</i>
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:text size="30" property="titleinvoiceattachment" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:text size="30" property="customername"
								value="${customername}" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:textarea style="width:100%" property="customeraddress"
										   rows="4" value="${customeraddress}" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle">
							&nbsp;
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left" colspan="3">
							<html:submit
								onclick="showPrint(this.form)"
								styleId="button"
								titleKey="main.invoice.button.createmaximumview.alttext.text">
								<bean:message key="main.general.button.printpreview.text" />
							</html:submit>

							<html:submit onclick="exportExcelNew(this.form)" styleId="button"
								titleKey="main.invoice.button.excel.new.text">
								<bean:message key="main.general.button.excelexport.new.text" />
							</html:submit>

							<select name="invoice-settings">
								<option value="HBT">HBT</option>
								<option value="NestorIT">NestorIT</option>
							</select>

						</td>
						<td class="noBborderStyle"></td>
					</tr>
				</table>
				<table>
					<tr>
						<td class="noBborderStyle">
							&nbsp;
						</td>
					</tr>
					<tr>
						<th>
							<bean:message key="main.invoice.title.print.text" />
						</th>
						<th>
							<bean:message key="main.invoice.title.suborder.text" />
							<html:hidden property="titlesubordertext" />
						</th>
						<c:if test="${showInvoiceForm.timereportsbox}">
							<th>
								<html:text property="titledatetext" />
							</th>
						</c:if>
						<c:if
							test="${showInvoiceForm.employeesignbox && showInvoiceForm.timereportsbox}">
							<th>
								<html:text property="titleemployeesigntext" />
							</th>
						</c:if>
						<th>
							<html:text property="titledescriptiontext" />
						</th>
						<c:if test="${showInvoiceForm.targethoursbox}">
							<th>
								<html:text property="titletargethourstext" />
							</th>
						</c:if>
						<c:if test="${showInvoiceForm.actualhoursbox}">
							<th>
								<html:text property="titleactualdurationtext" />
							</th>
							<th>
								<html:text property="titleactualhourstext" />
							</th>
						</c:if>
					</tr>
					<c:forEach var="suborderviewhelper" items="${viewhelpers}">
						<c:if test="${(suborderviewhelper.layer <= layerlimit) || (layerlimit eq -1)}">
						<tr>
							<!-- Checkbox -->
							<td>
								<html:multibox property="suborderIdArray" value="${suborderviewhelper.id}" />
							</td>
							<!-- Subordersign -->
							<td>
								<c:out value="${suborderviewhelper.getCompleteOrderSign()}"/>
							</td>
							<!-- Empty cell for timreport dates -->
							<c:if test="${showInvoiceForm.timereportsbox}">
								<td></td>
							</c:if>
							<!-- Empty cell if timereports createdby is active -->
							<c:if
								test="${showInvoiceForm.employeesignbox && showInvoiceForm.timereportsbox}">
								<td></td>
							</c:if>
							<!-- Long or short suborderdescription -->
							<td style="white-space: nowrap">
								<c:if test="${showInvoiceForm.suborderdescription eq 'longdescription'}">
									<c:out value="${suborderviewhelper.getCompleteOrderDescription(false, customeridbox)}"></c:out>
								</c:if>
								<c:if test="${showInvoiceForm.suborderdescription eq 'shortdescription'}">
									<c:out value="${suborderviewhelper.getCompleteOrderDescription(true, customeridbox)}"></c:out>
								</c:if>
							</td>
							<!-- Show targethours if active-->
							<c:if test="${showInvoiceForm.targethoursbox}">
								<td style="text-align: right;">
									<c:out value="${suborderviewhelper.debithoursString}"></c:out>
								</td>
							</c:if>
							<!-- actualhoursbox -->
							<c:if test="${showInvoiceForm.actualhoursbox}">
								<td style="text-align: right;">
									<c:out value="${suborderviewhelper.actualDuration}" />
								</td>
								<td style="text-align: right;">
									<c:out value="${suborderviewhelper.actualHours}" />
								</td>
							</c:if>
						</tr>
						<bean:size id="invoiceTimereportViewHelperListSize"
							name="suborderviewhelper"
							property="invoiceTimereportViewHelperList" />
						<c:if
							test="${invoiceTimereportViewHelperListSize>0 && showInvoiceForm.timereportsbox}">
							<c:forEach var="timereportviewhelper"
								items="${suborderviewhelper.invoiceTimereportViewHelperList}">
								<tr>
									<!-- Empty cell for suborderprintcheckbox -->
									<td class="noBborderStyle"></td>
									<td>
										<html:multibox property="timereportIdArray"
											value="${timereportviewhelper.id}" />
									</td>
									<!-- timereportdate -->
									<td>
										<java8:formatLocalDate value="${timereportviewhelper.referenceday}" pattern="dd.MM.yyyy" />
									</td>

									<c:if
										test="${showInvoiceForm.employeesignbox && showInvoiceForm.timereportsbox}">
										<td>
											<c:out
												value="${timereportviewhelper.employeeName}"></c:out>
										</td>
									</c:if>
									<c:if test="${showInvoiceForm.timereportdescriptionbox}">
										<td>
											<c:out value="${timereportviewhelper.taskdescription}"></c:out>
										</td>
									</c:if>
									<c:if test="${not showInvoiceForm.timereportdescriptionbox}">
										<td></td>
									</c:if>
									<c:if test="${showInvoiceForm.targethoursbox}">
										<td></td>
									</c:if>
									<c:if test="${showInvoiceForm.actualhoursbox}">
										<td style="text-align: right">
											<c:out value="${timereportviewhelper.durationString}" />
										</td>
										<td style="text-align: right">
											<c:out value="${timereportviewhelper.hoursString}" />
										</td>
									</c:if>
								</tr>
							</c:forEach>
						</c:if></c:if>
					</c:forEach>
					<c:if test="${showInvoiceForm.actualhoursbox}">
						<tr>
							<td class="noBborderStyle">
								&nbsp;
							</td>
							<td class="noBborderStyle">
								&nbsp;
							</td>
							<c:if test="${showInvoiceForm.timereportsbox}">
								<td class="noBborderStyle">
									&nbsp;
								</td>
							</c:if>
							<c:if
								test="${showInvoiceForm.employeesignbox && showInvoiceForm.timereportsbox}">
								<td class="noBborderStyle">
									&nbsp;
								</td>
							</c:if>
							<c:if test="${showInvoiceForm.targethoursbox}">
								<td class="noBborderStyle"></td>
								<td class="noBborderStyle" style="text-align: right;">
									<bean:message key="main.invoice.overall.text" />
								</td>
							</c:if>
							<c:if test="${not showInvoiceForm.targethoursbox}">
								<td class="noBborderStyle" style="text-align:right;">
									<b><bean:message key="main.invoice.overall.text" /></b>
								</td>
							</c:if>
							<th style="text-align: right;">
								<c:out value="${totaldurationsum}" />
							</th>
							<th style="text-align: right;">
								<c:out value="${totalhourssum}" />
							</th>
						</tr>
					</c:if>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:submit styleId="button"
								titleKey="main.invoice.button.createmaximumview.alttext.text">
								<bean:message key="main.general.button.printpreview.text" />
							</html:submit>
						</td>
						<td class="noBborderStyle"></td>
					</tr>
				</table>
			</html:form>
		</c:if>
	</body>
</html>
