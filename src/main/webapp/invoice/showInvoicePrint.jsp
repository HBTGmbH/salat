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
					<bean:message key="${dateMonth}" />&nbsp;<c:out value="${dateYear}" />
				</c:when>
				<c:when test="${invoiceview eq 'custom'}">
					<c:out value="${dateFirst}" /> - <c:out value="${dateLast}" />
				</c:when>
				<c:when test="${invoiceview eq 'week'}">
					<c:out value="${dateFirst}" /> - <c:out value="${dateLast}" /> (KW<c:out value="${currentWeek}" />)
				</c:when>
			</c:choose>
		</title>
		<link rel="stylesheet" type="text/css" href="/style/invoiceprint.css" media="all" />
		<link rel="stylesheet" type="text/css" href="/style/print.css" media="print" />
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
			<table style="clear: both">
				<tr>
					<td class="invoice_title">
						<b><c:out value="${titleinvoiceattachment}"/></b>
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
					<c:choose>
						<c:when test="${invoiceview eq 'month'}">
							<td class="invoice_time_reference">
								<bean:message key="${dateMonth}" />&nbsp;<c:out value="${dateYear}" />
							</td>
						</c:when>
						<c:when test="${invoiceview eq 'custom'}">
							<td class="invoice_time_reference">
								<c:out value="${dateFirst}" /> - <c:out value="${dateLast}" />
							</td>
						</c:when>
						<c:when test="${invoiceview eq 'week'}">
							<td class="invoice_time_reference">
								<c:out value="${dateFirst}" /> - <c:out value="${dateLast}" /> (KW<c:out value="${currentWeek}" />)
							</td>
						</c:when>
						<c:otherwise>
							<td class="invoice_time_reference">
								&nbsp;
							</td>
						</c:otherwise>
					</c:choose>
				</tr>
			</table>
			<br />
			<table width="100%" style="border-collapse: collapse">
				<tr class="invoice_header">
					<!-- Subordersign and Customersign -->
					<th class="invoice_header">
						<c:out value="${titlesubordertext}  " />
					</th>
					<c:if test="${customeridbox eq 'true'}">
						<th class="invoice_header">
							<c:out value="${titlecustomersigntext}" />
						</th>
					</c:if>
					<c:if test="${timereportsbox eq 'true'}">
						<th class="invoice_header">
							<c:out value="${titledatetext}" />
						</th>
					</c:if>
					<c:if test="${employeesignbox eq 'true'}">
						<th class="invoice_header">
							<c:out value="${titleemployeesigntext}" />
						</th>
					</c:if>
					<!-- Suborderdescription and targethours -->
					<th class="invoice_header" width="100%">
						<c:out value="${titledescriptiontext}" />
					</th>
					<c:if test="${targethoursbox eq 'true'}">
						<th class="invoice_header right">
							<c:out value="${titletargethourstext}" />
						</th>
					</c:if>
					<c:if test="${actualhoursbox eq 'true'}">
						<th class="invoice_header right">
							<c:out value="${titleactualdurationtext}" />
						</th>
						<th class="invoice_header right">
							<c:out value="${titleactualhourstext}" />
						</th>
					</c:if>
				</tr>
				<c:forEach var="suborderviewhelper" items="${viewhelpers}">
					<c:if test="${(suborderviewhelper.layer <= layerlimit) || (layerlimit eq -1)}">
						<c:if test="${suborderviewhelper.visible}">
							<tr class="invoice_suborder_row">
								<!-- Subordersign and Customersign -->
								<td class="invoice_suborder_row">
									<c:out value="${suborderviewhelper.sign}" />
								</td>
								<c:if test="${customeridbox eq 'true'}">
									<td class="invoice_suborder_row">
										<c:out value="${suborderviewhelper.suborder_customer}" />
									</td>
								</c:if>
								<c:if test="${timereportsbox eq 'true'}">
									<td class="invoice_suborder_row">
										&nbsp;
									</td>
								</c:if>
								<c:if test="${employeesignbox eq 'true'}">
									<td class="invoice_suborder_row">
										&nbsp;
									</td>
								</c:if>
								<c:if test="${suborderdescription eq 'longdescription'}">
									<td class="invoice_suborder_row wrap">
										<c:out value="${suborderviewhelper.description}" />
									</td>
								</c:if>
								<c:if test="${suborderdescription eq 'shortdescription'}">
									<td class="invoice_suborder_row wrap">
										<c:out value="${suborderviewhelper.shortdescription}" />
									</td>
								</c:if>
								<c:if test="${targethoursbox eq 'true'}">
									<td class="invoice_suborder_row right">
										<c:out value="${suborderviewhelper.debithoursString}" />
									</td>
								</c:if>
								<c:if test="${actualhoursbox eq 'true'}">
									<td class="invoice_suborder_row right">
										<c:out value="${suborderviewhelper.actualDurationPrint}" />
									</td>
									<td class="invoice_suborder_row right">
										<c:if test="${suborderviewhelper.layer < layerlimit || layerlimit eq -1}">
											<c:out value="${suborderviewhelper.actualhoursPrint}" />
										</c:if>
										<c:if test="${suborderviewhelper.layer eq layerlimit && !(layerlimit eq -1)}">
											<c:if test="${!(suborderviewhelper.duration eq '00:00') && !(suborderviewhelper.duration eq suborderviewhelper.actualhoursPrint)}">
												<c:out value="*" />
											</c:if>
											<c:out value="${suborderviewhelper.duration}" />
										</c:if>
									</td>
								</c:if>
							</tr>
							<bean:size id="invoiceTimereportViewHelperListSize" name="suborderviewhelper" property="invoiceTimereportViewHelperList" />
							<c:if test="${timereportsbox eq 'true' && invoiceTimereportViewHelperListSize > 0}">
								<c:forEach var="timereportviewhelper" items="${suborderviewhelper.invoiceTimereportViewHelperList}">
									<c:if test="${timereportviewhelper.visible}">
										<tr class="invoice_booking_row">
											<td class="invoice_booking_row">
												&nbsp;
											</td>
											<c:if test="${customeridbox eq 'true'}">
												<td class="invoice_booking_row">
													&nbsp;
												</td>
											</c:if>
											<td class="invoice_booking_row">
												<java8:formatLocalDate value="${timereportviewhelper.referenceday.refdate}"/>
											</td>
											<c:if test="${employeesignbox eq 'true' && timereportsbox eq 'true'}">
												<td class="invoice_booking_row">
													<c:out value="${timereportviewhelper.employeecontract.employee.name}" />
												</td>
											</c:if>
											<c:if test="${timereportdescriptionbox eq 'true'}">
												<td class="invoice_booking_row wrap">
													<c:out escapeXml="false" value="${timereportviewhelper.taskdescriptionHtml}" />
												</td>
											</c:if>
											<c:if test="${timereportdescriptionbox eq 'false'}">
												<td class="invoice_booking_row">
													&nbsp;
												</td>
											</c:if>
											<c:if test="${targethoursbox eq 'true'}">
												<td class="invoice_booking_row right">
													&nbsp;
												</td>
											</c:if>
											<c:if test="${actualhoursbox eq 'true'}">
												<td class="invoice_booking_row right">
													<c:out value="${timereportviewhelper.durationString}"/>
												</td>
												<td class="invoice_booking_row right">

												</td>
											</c:if>
										</tr>
									</c:if>
								</c:forEach>
							</c:if>
						</c:if>
					</c:if>
				</c:forEach>
				<c:if test="${actualhoursbox eq 'true'}">
					<tr class="invoice_totalsum_row">
						<td class="invoice_totalsum_row">
							&nbsp;
						</td>
						<c:if test="${customeridbox eq 'true'}">
							<td class="invoice_totalsum_row">
								&nbsp;
							</td>
						</c:if>
						<c:if test="${timereportsbox eq 'true'}">
							<td class="invoice_totalsum_row">
								&nbsp;
							</td>
						</c:if>
						<c:if test="${employeesignbox eq 'true' && timereportsbox eq 'true'}">
							<td class="invoice_totalsum_row">
								&nbsp;
							</td>
						</c:if>
						<c:if test="${targethoursbox eq 'true'}">
							<td class="invoice_totalsum_row">
								&nbsp;
							</td>
							<td class="invoice_totalsum_row right">
								<b><bean:message key="main.invoice.overall.text" />:</b>
							</td>
						</c:if>
						<c:if test="${targethoursbox eq 'false'}">
							<td class="invoice_totalsum_row right">
								<b><bean:message key="main.invoice.overall.text" />:</b>
							</td>
						</c:if>
						<td class="invoice_totalsum_row right">
							<java8:formatDuration value="${actualminutessum}" />
						</td>
						<td class="invoice_totalsum_row right">
							<c:out value="${printactualhourssum}" />
						</td>
					</tr>
				</c:if>
			</table>
		</div>
	</body>
</html>
