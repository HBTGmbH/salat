<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html>
	<head>

		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
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
		<link rel="stylesheet" type="text/css" href="/style/print.css" media="print" />
		<link rel="stylesheet" type="text/css" href="/style/invoiceprint.css" media="all" />
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
				<img src="/images/HBT_Logo_RGB_positiv.svg" class="hbt_logo" />
				<img src="/images/HBT_Claim_RGB_positiv.svg" class="hbt_claim" />
			</div>
			<table style="clear: both; float: left">
				<tr>
					<td class="invoice_title">
						<c:out value="${titleinvoiceattachment}"/>
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
						<c:out value="${customername}" />
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
						<c:out escapeXml="false" value="${customeraddress}" />
					</td>
				</tr>
				<tr>
					<td class="invoice_address_line">
					</td>
				</tr>
				<tr>
					<td class="invoice_time_reference">
						Zeitraum:
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
					</td>
				</tr>
			</table>
			<span style="float: right" class="invoice_date">Stand: <java8:formatLocalDate value="${today}" pattern="dd.MM.yyyy" /></span>
			<br style="clear: both" />
			<c:forEach var="suborderviewhelper" items="${viewhelpers}">
				<table width="100%" style="border-collapse: collapse">
					<c:if test="${suborderviewhelper.visible and (suborderviewhelper.layer <= layerlimit or layerlimit eq -1)}">
						<thead>
							<tr class="invoice_suborder_row">
								<td class="invoice_suborder_row wrap" colspan="${dynamicColumnCount + 1}">
									<c:if test="${optionsuborderdescription eq 'longdescription'}">
										<c:out value="${suborderviewhelper.getCompleteOrderDescription(false)}"></c:out>
									</c:if>
									<c:if test="${optionsuborderdescription eq 'shortdescription'}">
										<c:out value="${suborderviewhelper.getCompleteOrderDescription(true)}"></c:out>
									</c:if>
									<c:if test="${customeridbox and not empty suborderviewhelper.suborder_customer}">
										/ <c:out value="${suborderviewhelper.suborder_customer}" />
									</c:if>
									<c:if test="${targethoursbox and not empty suborderviewhelper.debithoursString}">
										/ Budget: <c:out value="${suborderviewhelper.debithoursString}" />
									</c:if>
								</td>
							</tr>
							<bean:size id="invoiceTimereportViewHelperListSize" name="suborderviewhelper" property="invoiceTimereportViewHelperList" />
							<c:if test="${timereportsbox && invoiceTimereportViewHelperListSize > 0}">
								<tr class="invoice_header">
									<c:if test="${timereportsbox}">
										<th class="invoice_header">
											<c:out value="${titledatetext}" />
										</th>
									</c:if>
									<c:if test="${employeesignbox}">
										<th class="invoice_header">
											<c:out value="${titleemployeesigntext}" />
										</th>
									</c:if>
									<!-- Suborderdescription and targethours -->
									<th class="invoice_header" width="100%">
										<c:out value="${titledescriptiontext}" />
									</th>
									<c:if test="${actualhoursbox}">
										<th class="invoice_header right">
											<c:out value="${titleactualdurationtext}" />
										</th>
										<th class="invoice_header right">
											<c:out value="${titleactualhourstext}" />
										</th>
									</c:if>
								</tr>
							    </thead>
							    <tbody>
								<c:forEach var="timereportviewhelper" items="${suborderviewhelper.invoiceTimereportViewHelperList}" varStatus="iterstatus">
									<c:if test="${timereportviewhelper.visible}">
										<tr class="invoice_booking_row ${iterstatus.last?'last_timereport':''}">
											<td class="invoice_booking_row nonproportional ${iterstatus.last?'last_timereport':''}">
												<java8:formatLocalDate value="${timereportviewhelper.referenceday}" pattern="dd.MM.yyyy" />
											</td>
											<c:if test="${employeesignbox && timereportsbox}">
												<td class="invoice_booking_row ${iterstatus.last?'last_timereport':''}">
													<c:out value="${timereportviewhelper.employeeName}" />
												</td>
											</c:if>
											<c:if test="${timereportdescriptionbox}">
												<td class="invoice_booking_row wrap ${iterstatus.last?'last_timereport':''}"
													style="width: 100%">
													<c:out escapeXml="false" value="${timereportviewhelper.taskdescriptionHtml}" />
												</td>
											</c:if>
											<c:if test="${timereportdescriptionbox eq 'false'}">
												<td class="invoice_booking_row ${iterstatus.last?'last_timereport':''}"
													style="width: 100%">
													&nbsp;
												</td>
											</c:if>
											<c:if test="${actualhoursbox}">
												<td class="invoice_booking_row nonproportional right ${iterstatus.last?'last_timereport':''}"
													style="min-width: 2cm">
													<c:out value="${timereportviewhelper.durationString}"/>
												</td>
												<td class="invoice_booking_row nonproportional right ${iterstatus.last?'last_timereport':''}"
													style="min-width: 2cm">
													<c:out value="${timereportviewhelper.hoursString}"/>
												</td>
											</c:if>
										</tr>
									</c:if>
								</c:forEach>
								</tbody>
							</c:if>
						    <c:if test="${not (timereportsbox && invoiceTimereportViewHelperListSize > 0)}">
							    </thead>
							</c:if>
							<c:if test="${actualhoursbox}">
								<tbody>
								<tr class="invoice_subordersum_row">
									<td class="invoice_subordersum_row right" colspan="${dynamicColumnCount + 1 - 2}" style="width: 100%">
										Summe
									</td>
									<td class="invoice_subordersum_row nonproportional right" style="min-width: 2cm">
										<c:out value="${suborderviewhelper.actualDurationPrint}" />
									</td>
									<td class="invoice_subordersum_row nonproportional right" style="min-width: 2cm">
										<c:out value="${suborderviewhelper.actualHoursPrint}" />
									</td>
								</tr>
								</tbody>
							</c:if>
					</c:if>
				</table>
			</c:forEach>
			<c:if test="${actualhoursbox}">
				<table width="100%" style="border-collapse: collapse">
					<tbody>
					<tr class="invoice_totalsum_row">
						<td class="invoice_totalsum_row right" style="width: 100%" colspan="${timereportsbox?'3':'1'}">
							<bean:message key="main.invoice.overall.text" />
						</td>
						<td class="invoice_totalsum_row nonproportional right" style="min-width: 2cm">
							<java8:formatDuration value="${actualminutessum}" />
						</td>
						<td class="invoice_totalsum_row nonproportional right" style="min-width: 2cm">
							<c:out value="${printactualhourssum}" />
						</td>
					</tr>
					</tbody>
				</table>
			</c:if>
		</div>
	</body>
</html>
