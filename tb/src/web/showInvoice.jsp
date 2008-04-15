<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>

<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
	<head>
		<meta http-equiv="Content-Type"
			content="text/html; charset=ISO-8859-1">
		<title><bean:message key="main.general.application.title" />
			- <bean:message key="main.general.mainmenu.invoice.title.text" />
		</title>
		<link rel="stylesheet" type="text/css" href="/tb/tb.css" media="all" />
		<link rel="stylesheet" type="text/css" href="/tb/print.css"
			media="print" />
		<script type="text/javascript" language="JavaScript">	
 	function setUpdateInvoiceAction(form) {	
 		form.action = "/tb/do/ShowInvoice?task=refreshInvoiceForm";
		form.submit();
	}
	function exportExcel(form) {
		form.action = "/tb/do/ShowInvoice?task=export";
		form.submit();
	}
	
	function showPrint(form) {
		form.action = "/tb/do/ShowInvoice?task=print";
		form.submit();
	}
	
</script>
	</head>
	<body>
		<jsp:include flush="true" page="/menu.jsp">
			<jsp:param name="title" value="Menu" />
		</jsp:include>
		<br>
		<span style="font-size: 14pt; font-weight: bold;"><br> <bean:message
				key="main.general.mainmenu.invoice.title.text" />
			<br> </span>
		<br>
		<html:form action="/ShowInvoice?task=generateMaximumView">
			<table class="center backgroundcolor">
				<tr>
					<td colspan="2" align="left" class="noBborderStyle">
						<hr>
					</td>
				</tr>
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
						<html:select property="order"
							value="<%=(String) request.getSession().getAttribute(
									"currentOrder")%>"
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
					<td align="left" class="noBborderStyle"></td>
					<td align="left" class="noBborderStyle">
						<html:select property="suborder"
							value="<%=(String) request.getSession().getAttribute(
									"currentSuborder")%>"
							onchange="setUpdateInvoiceAction(this.form)">

							<html:option value="ALL SUBORDERS">
								<bean:message key="main.general.allsuborders.text" />
							</html:option>

							<html:options collection="suborders"
								labelProperty="signAndDescription" property="id" />

						</html:select>
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
						<c:otherwise>
							<td align="left" class="noBborderStyle">
								<b><bean:message key="main.monthlyreport.daymonthyear.text" />:</b>
							</td>
						</c:otherwise>
					</c:choose>

					<td align="left" class="noBborderStyle">
						<c:if test="${!(invoiceview eq 'month')}">
							<html:select property="fromDay" value="${currentDay}"
								onchange="setUpdateInvoiceAction(this.form)">
								<html:options collection="days" property="value"
									labelProperty="label" />
							</html:select>
						</c:if>
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
				<tr>
					<td colspan="2" align="left" class="noBborderStyle">
						<hr>
					</td>
				</tr>
				<tr>

					<td class="noBborderStyle" align="left">
						<html:submit styleId="button"
							titleKey="main.invoice.button.createmaximumview.alttext.text">
							<bean:message key="main.invoice.button.createmaximumview.text" />
						</html:submit>
					</td>
					<td class="noBborderStyle"></td>
				</tr>
				<tr>
					<td colspan="2" align="left" class="noBborderStyle">
						<hr>
					</td>
				</tr>
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
				<c:if test="${timereportsubboxes}">
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
						<html:checkbox property="actualhoursbox"
							onchange="setUpdateInvoiceAction(this.form)" />
					</td>
				</tr>
				<tr>
					<td colspan="2" align="left" class="noBborderStyle">
						<hr>
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
				<!-- select value added tax 
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.invoice.mwst.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="mwst" value="${optionmwst}" size="2" maxlength="2"
				onchange="setUpdateInvoiceAction(this.form)" /></td>
		</tr>-->


			</table>
		</html:form>

		<table>
			<html:form target="fenster" action="/ShowInvoice?task=print">
				<c:if test="${! empty viewhelpers}">
					<tr>
						<td class="noBborderStyle" align="left">
							<i>Hinweis: Bearbeiten der Anzeigeoptionen setzt die Änderung
								der Adresse zurück</i>
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
								value="${customeraddress}" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle">
							&nbsp;
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:submit
								onclick="window.open('','fenster','width=800,height=400,resizable=yes'); showPrint(this.form)"
								styleId="button"
								titleKey="main.invoice.button.createmaximumview.alttext.text">
								<bean:message key="main.general.button.printpreview.text" />
							</html:submit>

							<html:submit onclick="exportExcel(this.form)" styleId="button"
								titleKey="main.invoice.button.excel.text">
					<bean:message key="main.general.button.excelexport.text" />
				</html:submit>

						</td>
						<td class="noBborderStyle"></td>
					</tr>
					<!-- 			<tr><td align="left" class="noBborderStyle"><html:text size="30" property="titleinvoiceattachment" /></td></tr> -->
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
							<html:text property="titlesubordertext" />
						</th>
						<!-- Subordersign and Customersign -->
						<c:if test="${param.customeridbox eq 'on'}">
							<th>
								<html:text property="titlecustomersigntext" />
							</th>
						</c:if>
						<c:if test="${param.timereportsbox eq 'on'}">
							<th>
								<html:text property="titledatetext" />
							</th>
						</c:if>
						<c:if
							test="${param.employeesignbox eq 'on' && param.timereportsbox eq 'on'}">
							<th>
								<html:text property="titleemployeesigntext" />
							</th>
						</c:if>
						<th width="400px">
							<html:text property="titledescriptiontext" />
						</th>
						<c:if test="${param.targethoursbox eq 'on'}">
							<th width="22px">
								<html:text property="titletargethourstext" />
							</th>
						</c:if>
						<c:if test="${param.actualhoursbox eq 'on'}">
							<th width="22px">
								<html:text property="titleactualhourstext" />
							</th>
						</c:if>
					</tr>
					<c:forEach var="suborderviewhelper" items="${viewhelpers}">
						<c:if test="${(suborderviewhelper.layer <= layerlimit) || (layerlimit eq -1)}">
						<tr>
							<!-- Checkbox -->
							<td>
								<html:multibox property="suborderIdArray"
									value="${suborderviewhelper.id}" />
							</td>
							<!-- Subordersign -->
							<td>
								<c:out value="${suborderviewhelper.sign}"/>
							</td>
							<!-- Employeesign -->
							<c:if test="${param.customeridbox eq 'on'}">
								<td>
									<c:out value="${suborderviewhelper.suborder_customer}" />
								</td>
							</c:if>
							<!-- Empty cell for timreport dates -->
							<c:if test="${param.timereportsbox eq 'on'}">
								<td></td>
							</c:if>
							<!-- Empty cell if timereports createdby is active -->
							<c:if
								test="${param.employeesignbox eq 'on' && param.timereportsbox eq 'on'}">
								<td></td>
							</c:if>
							<!-- Long or short suborderdescription -->
							<c:if test="${param.suborderdescription eq 'longdescription'}">
								<td>
									<c:out value="${suborderviewhelper.description}"></c:out>
								</td>
							</c:if>
							<c:if test="${param.suborderdescription eq 'shortdescription'}">
								<td>
									<c:out value="${suborderviewhelper.shortdescription}"></c:out>
								</td>
							</c:if>
							<!-- Show targethours if active-->
							<c:if test="${param.targethoursbox eq 'on'}">
								<td style="text-align: right;">
									<c:out value="${suborderviewhelper.debithoursString}"></c:out>
								</td>
							</c:if>
							<!-- targethours -->
							<c:if test="${param.actualhoursbox eq 'on'}">
								<td style="text-align: right;">
									<c:if test="${suborderviewhelper.layer < layerlimit || layerlimit eq -1}"><c:out value="${suborderviewhelper.actualhours}"></c:out></c:if>
									<c:if test="${suborderviewhelper.layer eq layerlimit && !(layerlimit eq -1)}"><c:if test="${!(suborderviewhelper.duration eq '0:00') && !(suborderviewhelper.duration eq suborderviewhelper.actualhours)}">*</c:if> <c:out value="${suborderviewhelper.duration}"></c:out></c:if>
								</td>
							</c:if>
						</tr>
						<bean:size id="invoiceTimereportViewHelperListSize"
							name="suborderviewhelper"
							property="invoiceTimereportViewHelperList" />
						<c:if
							test="${invoiceTimereportViewHelperListSize>0 && param.timereportsbox eq 'on'}">
							<c:forEach var="timereportviewhelper"
								items="${suborderviewhelper.invoiceTimereportViewHelperList}">
								<tr>
									<!-- Empty cell for suborderprintcheckbox -->
									<td class="noBborderStyle"></td>
									<!-- Checkbox and empty cell if employeesign is active-->
									<c:if test="${param.customeridbox eq 'on'}">
										<td class="noBborderStyle"></td>
										<td>
											<html:multibox property="timereportIdArray"
												value="${timereportviewhelper.id}" />
										</td>
									</c:if>
									<c:if test="${empty param.customeridbox}">
										<td>
											<html:multibox property="timereportIdArray"
												value="${timereportviewhelper.id}" />
										</td>
									</c:if>
									<!-- timereportdate -->
									<td>
										<fmt:formatDate
											value="${timereportviewhelper.referenceday.refdate}"
											pattern="dd.MM.yyyy" />
									</td>

									<c:if
										test="${param.employeesignbox eq 'on' && param.timereportsbox eq 'on'}">
										<td>
											<c:out
												value="${timereportviewhelper.employeecontract.employee.sign}"></c:out>
										</td>
									</c:if>
									<c:if test="${param.timereportdescriptionbox eq 'on'}">
										<td>
											<c:out value="${timereportviewhelper.taskdescription}"></c:out>
										</td>
									</c:if>
									<c:if test="${empty param.timereportdescriptionbox}">
										<td></td>
									</c:if>
									<c:if test="${param.targethoursbox eq 'on'}">
										<td></td>
									</c:if>
									<c:if test="${param.actualhoursbox eq 'on'}">
										<td>
											<c:out value="${timereportviewhelper.durationhours}" />:<c:if test="${timereportviewhelper.durationminutes<10}">0</c:if><c:out value="${timereportviewhelper.durationminutes}" />
										</td>
									</c:if>
								</tr>
							</c:forEach>
						</c:if></c:if>
					</c:forEach>
					<c:if test="${param.actualhoursbox eq 'on'}">
						<tr>
							<td class="noBborderStyle"></td>
							<td class="noBborderStyle"></td>
							<c:if test="${param.customeridbox eq 'on'}">
								<td class="noBborderStyle"></td>
							</c:if>
							<c:if test="${param.timereportsbox eq 'on'}">
								<td class="noBborderStyle"></td>
							</c:if>
							<c:if
								test="${param.employeesignbox eq 'on' && param.timereportsbox eq 'on'}">
								<td class="noBborderStyle"></td>
							</c:if>
							<c:if test="${param.targethoursbox eq 'on'}">
								<td class="noBborderStyle"></td>
								<td class="noBborderStyle" style="text-align: right;">
									<bean:message key="main.invoice.overall.text" />
									:
								</td>
							</c:if>
							<c:if test="${empty param.targethoursbox}">
								<td class="noBborderStyle style="text-align:right;">
									<bean:message key="main.invoice.overall.text" />
									:
								</td>
							</c:if>
							<th style="text-align: right;">
								<c:out value="${targethourssum}" />
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
				</c:if>
		</table>
		</html:form>
	</body>
</html>
