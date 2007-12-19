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
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.invoice.title.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/invoiceprint.css"
	media="all" />
<link rel="stylesheet" type="text/css" href="/tb/print.css"
	media="print" />
</head>
<body>
<FORM ONSUBMIT="javascript:window.print();return false;">
<div align="right"><input class="hiddencontent" type="submit"
	value="Drucken"></div>
<table>
	<tr>
		<td align="left" class="matrix hiddencontent" style="border: 0px;"><bean:message
			key="main.invoice.scroll.text" /></td>
	</tr>
</table>
</form>
<table>
	<tr>
		<td align="left" class="matrix" style="border: 0px;"><b><c:out
			value="${titleinvoiceattachment}" /></b></td>
	</tr>
	<tr>
		<td align="left" class="matrix" style="border: 0px;"></td>
	</tr>
	<tr>
		<td align="left" class="matrix" style="border: 0px;"><c:out
			value="${customername}" /></td>
	</tr>
	<tr>
		<td align="left" class="matrix" style="border: 0px;"><c:out
			escapeXml="false" value="${customeraddress}" /></td>
	</tr>
	<tr>
		<td align="left" class="matrix" style="border: 0px;"></td>
	</tr>
	<tr>
		<c:if test="${invoiceview eq 'month'}">
			<td align="left" class="matrix" style="border: 0px;"><bean:message
				key="${dateMonth}" />&nbsp;<c:out value="${dateYear}" /></td>
		</c:if>
		<c:if test="${invoiceview eq 'custom'}">
			<td align="left" class="matrix" style="border: 0px;"><c:out
				value="${dateFirst}" /> - <c:out value="${dateLast}" /></td>
		</c:if>
	</tr>
</table>
<br />
<table style="border:1px black solid;" class="matrix" width="100%">
	<c:if test="${! empty viewhelpers}">
		<tr class="matrix">
			<!-- Subordersign and Customersign -->
			<th class="matrix"><c:out value="${titlesubordertext}" /></th>
			<c:if test="${customeridbox eq 'true'}">
				<th class="matrix"><c:out value="${titlecustomersigntext}" /></th>
			</c:if>
			<c:if test="${timereportsbox eq 'true'}">
				<th class="matrix"><c:out value="${titledatetext}" /></th>
			</c:if>
			<c:if test="${employeesignbox eq 'true'}">
				<th class="matrix"><c:out value="${titleemployeesigntext}" /></th>
			</c:if>
			<!-- Suborderdescription and targethours -->
			<th class="matrix" width="70%"><c:out
				value="${titledescriptiontext}" /></th>
			<c:if test="${targethoursbox eq 'true'}">
				<th class="matrix"><c:out value="${titletargethourstext}" /></th>
			</c:if>
			<c:if test="${actualhoursbox eq 'true'}">
				<th class="matrix"><c:out value="${titleactualhourstext}" /></th>
			</c:if>
		</tr>
		<c:forEach var="suborderviewhelper" items="${viewhelpers}">
			<c:if test="${suborderviewhelper.visible}">
				<tr class="matrix" style="background-color:c1c1c1;">
					<!-- Subordersign and Customersign -->
					<td class="matrix"><c:out value="${suborderviewhelper.sign}"></c:out></td>
					<c:if test="${customeridbox eq 'true'}">
						<td class="matrix"><c:out
							value="${suborderviewhelper.suborder_customer}" /></td>
					</c:if>
					<c:if test="${timereportsbox eq 'true'}">
						<td class="matrix"></td>
					</c:if>
					<c:if test="${employeesignbox eq 'true'}">
						<td class="matrix"></td>
					</c:if>
					<c:if test="${suborderdescription eq 'longdescription'}">
						<td class="matrix"><c:out
							value="${suborderviewhelper.description}"></c:out></td>
					</c:if>
					<c:if test="${suborderdescription eq 'shortdescription'}">
						<td class="matrix"><c:out
							value="${suborderviewhelper.shortdescription}"></c:out></td>
					</c:if>
					<c:if test="${targethoursbox eq 'true'}">
						<td class="matrix" style="text-align: right;"><c:out
							value="${suborderviewhelper.debithours}"></c:out></td>
					</c:if>
					<c:if test="${actualhoursbox eq 'true'}">
						<td class="matrix" style="text-align: right;"><c:out
							value="${suborderviewhelper.actualhours}"></c:out></td>
					</c:if>
				</tr>
				<bean:size id="invoiceTimereportViewHelperListSize"
					name="suborderviewhelper"
					property="invoiceTimereportViewHelperList" />
				<c:if
					test="${invoiceTimereportViewHelperListSize>0 && timereportsbox eq 'true'}">


					<c:forEach var="timereportviewhelper"
						items="${suborderviewhelper.invoiceTimereportViewHelperList}">
						<c:if test="${timereportviewhelper.visible}">
							<tr class="matrix">
								<td class="matrix"></td>
								<c:if test="${customeridbox eq 'true'}">
									<td class="matrix"></td>
								</c:if>
								<td class="matrix"><fmt:formatDate
									value="${timereportviewhelper.referenceday.refdate}"
									pattern="dd.MM.yyyy" /></td>
								<c:if
									test="${employeesignbox eq 'true' && timereportsbox eq 'true'}">
									<td class="matrix"><c:out
										value="${timereportviewhelper.createdby}"></c:out></td>
								</c:if>
								<c:if test="${timereportdescriptionbox eq 'true'}">
									<td class="matrix"><c:out
										value="${timereportviewhelper.taskdescription}"></c:out></td>

								</c:if>
								<c:if test="${timereportdescriptionbox eq 'false'}">
									<td class="matrix"></td>
								</c:if>
								<c:if test="${targethoursbox eq 'true'}">
									<td class="matrix"></td>
								</c:if>
								<c:if test="${actualhoursbox eq 'true'}">
									<td class="matrix"><c:out
										value="${timereportviewhelper.durationhours}" />:<c:out
										value="${timereportviewhelper.durationminutes}" /></td>
								</c:if>
							</tr>
						</c:if>
					</c:forEach>
				</c:if>
			</c:if>
		</c:forEach>
		<c:if test="${actualhoursbox eq 'true'}">
			<tr class="matrix">
				<td class="matrix" class="noBborderStyle"></td>
				<c:if test="${customeridbox eq 'true'}">
					<td class="matrix" class="noBborderStyle"></td>
				</c:if>
				<c:if test="${timereportsbox eq 'true'}">
					<td class="matrix" class="noBborderStyle"></td>
				</c:if>
				<c:if
					test="${employeesignbox eq 'true' && timereportsbox eq 'true'}">
					<td class="matrix" class="noBborderStyle"></td>
				</c:if>
				<c:if test="${targethoursbox eq 'true'}">
					<td class="matrix" class="noBborderStyle"></td>
					<td class="matrix" class="noBborderStyle"
						style="text-align: right;"><b><bean:message
						key="main.invoice.overall.text" />:</b></td>
				</c:if>
				<c:if test="${targethoursbox eq 'false'}">
					<td class="matrix" class="noBborderStyle"
						style="text-align: right;"><b><bean:message
						key="main.invoice.overall.text" />:</b></td>
				</c:if>
				<th class="matrix" style="text-align: right;"><c:out
					value="${printactualhourssum}" /></th>
			</tr>
		</c:if>
	</c:if>
</table>
</body>
</html>
