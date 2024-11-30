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
			<bean:message key="main.general.mainmenu.invoice.title.text" /> /
			<c:out value="${customername}" /> /
			<c:out value="${order}" /> /
			<c:choose>
				<c:when test="${invoiceview eq 'month'}">
					<bean:message key="${dateMonth}" /> <c:out value="${dateYear}" />
				</c:when>
				<c:when test="${invoiceview eq 'custom'}">
					<c:out value="${dateFirst}" /> - <c:out value="${dateLast}" />
				</c:when>
				<c:when test="${invoiceview eq 'week'}">
					<c:out value="${dateFirst}" /> - <c:out value="${dateLast}" /> (KW<c:out value="${currentWeek}" />)
				</c:when>
			</c:choose>
		</title>
		<link rel="stylesheet" type="text/css" href="<c:url value="/style/print.css" />" media="print" />
		<link rel="stylesheet" type="text/css" href="<c:url value="/style/invoiceprint.css" />" media="all" />
		<style>
			${customCss}
		</style>
	</head>
	<body>
		<div style="width: 95%; margin: 0 auto">
			<form onsubmit="javascript:window.print();return false;" class="hiddencontent">
				<div align="right">
					<input class="hiddencontent" type="submit" value="Drucken">
				</div>
				<div class="invoice_hint">
					<bean:message key="main.invoice.scroll.text" />
				</div>
			</form>
			<div>
				<img src="<c:url value="${logoUrl}"/>" class="hbt_logo" />
				<img src="<c:url value="${claimUrl}"/>" class="hbt_claim" />
			</div>
			<table style="clear: both; float: left">
				<tr>
					<td class="invoice_title">
						<c:out value="${showInvoiceForm.titleinvoiceattachment}"/>
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
						<c:out value="${showInvoiceForm.customername}" />
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
						<c:out escapeXml="false" value="${showInvoiceForm.customeraddressFormatted}" />
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
					</td>
				</tr>
				<tr>
					<td class="invoice_time_reference">
						Zeitraum: <java8:formatLocalDate value="${invoiceData.invoiceDateRange.from}" pattern="dd.MM.yyyy" /> - <java8:formatLocalDate value="${invoiceData.invoiceDateRange.until}" pattern="dd.MM.yyyy" />
					</td>
				</tr>
			</table>
			<span style="float: right" class="invoice_date">Stand: <java8:formatLocalDate value="${today}" pattern="dd.MM.yyyy" /></span>
			<br style="clear: both" />
			<c:forEach var="invoiceSuborder" items="${invoiceData.suborders}">
				<c:if test="${invoiceSuborder.visible}">
					<table width="100%" style="border-collapse: collapse">
						<thead>
							<tr class="invoice_suborder_row">
								<td class="invoice_suborder_row wrap" colspan="${dynamicColumnCount + 2}">
									<c:out value="${invoiceSuborder.orderDescription}"></c:out>
									<c:if test="${targethoursbox and not empty invoiceSuborder.debithoursString}">
										/ Budget: <fmt:formatNumber value="${invoiceSuborder.budget}" minFractionDigits="2" maxFractionDigits="2" />
									</c:if>
								</td>
							</tr>
							<bean:size id="timereportsSize" name="invoiceSuborder" property="timereports" />
							<tr class="invoice_header">
								<c:if test="${showInvoiceForm.timereportsbox && timereportsSize > 0}">
									<th class="invoice_header">
										<c:out value="${showInvoiceForm.titledatetext}" />
									</th>
									<c:if test="${showInvoiceForm.employeesignbox}">
										<th class="invoice_header">
											<c:out value="${showInvoiceForm.titleemployeesigntext}" />
										</th>
									</c:if>
									<c:if test="${showInvoiceForm.timereportdescriptionbox}">
										<th class="invoice_header" width="100%">
											<c:out value="${showInvoiceForm.titledescriptiontext}" />
										</th>
									</c:if>
								</c:if>
								<th class="invoice_header right">
									<c:out value="${showInvoiceForm.titleactualdurationtext}" />
								</th>
								<th class="invoice_header right">
									<c:out value="${showInvoiceForm.titleactualhourstext}" />
								</th>
							</tr>
						</thead>
						<tbody>
							<c:forEach var="invoiceTimereport" items="${invoiceSuborder.timereports}" varStatus="iterstatus">
								<c:if test="${invoiceTimereport.visible}">
									<tr class="invoice_booking_row ${iterstatus.last?'last_timereport':''}">
										<td class="invoice_booking_row nonproportional ${iterstatus.last?'last_timereport':''}">
											<java8:formatLocalDate value="${invoiceTimereport.referenceDay}" pattern="dd.MM.yyyy" />
										</td>
										<c:if test="${showInvoiceForm.employeesignbox}">
											<td class="invoice_booking_row ${iterstatus.last?'last_timereport':''}">
												<c:out value="${invoiceTimereport.employeeName}" />
											</td>
										</c:if>
										<c:if test="${showInvoiceForm.timereportdescriptionbox}">
											<td class="invoice_booking_row wrap ${iterstatus.last?'last_timereport':''}"
												style="width: 100%">
												<c:out escapeXml="false" value="${invoiceTimereport.taskDescription}" />
											</td>
										</c:if>
										<td class="invoice_booking_row nonproportional right ${iterstatus.last?'last_timereport':''}"
											style="min-width: 2cm">
											<java8:formatDuration value="${invoiceTimereport.duration}"/>
										</td>
										<td class="invoice_booking_row nonproportional right ${iterstatus.last?'last_timereport':''}"
											style="min-width: 2cm">
										</td>
									</tr>
								</c:if>
							</c:forEach>
							<tr class="invoice_subordersum_row">
								<td class="invoice_subordersum_row right" colspan="${dynamicColumnCount}" style="width: 100%">
									Summe
								</td>
								<td class="invoice_subordersum_row nonproportional right" style="min-width: 2cm">
									<java8:formatDuration value="${invoiceSuborder.totalDuration}" />
								</td>
								<td class="invoice_subordersum_row nonproportional right" style="min-width: 2cm">
									<fmt:formatNumber value="${invoiceSuborder.totalHours}" minFractionDigits="2" maxFractionDigits="2" />
								</td>
							</tr>
						</tbody>
					</table>
				</c:if>
			</c:forEach>
			<table width="100%" style="border-collapse: collapse">
				<tbody>
				<tr class="invoice_totalsum_row">
					<td class="invoice_totalsum_row right" style="width: 100%" colspan="${dynamicColumnCount}">
						<bean:message key="main.invoice.overall.text" />
					</td>
					<td class="invoice_totalsum_row nonproportional right" style="min-width: 2cm">
						<java8:formatDuration value="${invoiceData.totalDurationVisible}" />
					</td>
					<td class="invoice_totalsum_row nonproportional right" style="min-width: 2cm">
						<c:out value="${invoiceData.totalHoursVisible}" />
					</td>
				</tr>
				</tbody>
			</table>
		</div>
	</body>
</html>
