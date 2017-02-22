<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>


<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="statusreport.pagetitle.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<link href="/tb/style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />
<script src="/tb/scripts/jquery-1.11.3.min.js"></script>
<script src="/tb/scripts/select2.full.min.js"></script>

<script type="text/javascript" language="JavaScript">	
	
	function refresh(form) {
		form.action = "/tb/do/ShowStatusReport?task=refresh";
		form.submit();
	}
	
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteStatusReport?srId=" + id;
			form.submit();
		}
	}					
	
	function editReport(form, id) {	
		form.action = "/tb/do/EditStatusReport?srId=" + id;
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

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'element'
		});	
	});		
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size: 14pt; font-weight: bold;"><br>
<bean:message key="statusreport.headline.text" /><br>
</span>
<br>
<span style="color: red"><html:errors footer="<br>" /> </span>

<table class="center backgroundcolor">
	<html:form action="/ShowStatusReport?task=refresh">
		<tr>
			<td class="noBborderStyle" colspan="3"><b><bean:message
				key="main.suborder.customerorder.text" /></b></td>
			<td class="noBborderStyle" colspan="8" align="left"><html:select
				property="customerOrderId" onchange="refresh(this.form)" styleClass="make-select2">
				<html:option value="-1">
					<bean:message key="main.general.allorders.text" />
				</html:option>
				<html:options collection="visibleCustomerOrders"
					labelProperty="signAndDescription" property="id" />
			</html:select></td>
		</tr>
		<tr>
			<td class="noBborderStyle" colspan="3"><b><bean:message
				key="main.customerorder.statusreport.hidereleased" /></b></td>
			<td class="noBborderStyle" colspan="8" align="left"><html:checkbox
				property="showReleased" onchange="refresh(this.form)" /></td>
		</tr>
	</html:form>
	<bean:size id="statusReportsSize" name="statusReports" />
	<c:if test="${statusReportsSize>10}">
		<tr>
			<html:form action="/CreateStatusReport">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button">
					<bean:message key="statusreport.button.create.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
	<tr>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.info.title.text" />"><b><bean:message
			key="statusreport.table.headline.info.text" /></b></th>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.order.title.text" />"><b><bean:message
			key="statusreport.table.headline.order.text" /></b></th>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.orderdescription.title.text" />"><b><bean:message
			key="statusreport.table.headline.orderdescription.text" /></b></th>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.sort.title.text" />"><b><bean:message
			key="statusreport.table.headline.sort.text" /></b></th>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.status.title.text" />"><b><bean:message
			key="statusreport.table.headline.status.text" /></b></th>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.timeperiod.title.text" />"><b><bean:message
			key="statusreport.table.headline.timeperiod.text" /></b></th>
		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.from.title.text" />"><b><bean:message
			key="statusreport.table.headline.from.text" /></b></th>

		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.released.title.text" />"><b><bean:message
			key="statusreport.table.headline.released.text" /></b></th>

		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.to.title.text" />"><b><bean:message
			key="statusreport.table.headline.to.text" /></b></th>



		<th align="left"
			title="<bean:message 
			key="statusreport.table.headline.accepted.title.text" />"><b><bean:message
			key="statusreport.table.headline.accepted.text" /></b></th>
		<th align="center"
			title="<bean:message
			key="statusreport.table.headline.edit.title.text" />"><b><bean:message
			key="statusreport.table.headline.edit.text" /></b></th>
		<% int i = 0; %>
		<c:forEach var="statusreport" items="${statusReports}"
			varStatus="statusID">
			<c:if
				test="${!showReleased || (statusreport.accepted == null || statusreport.released == null)}">
				<%if (i%2 == 0) { %>
				<tr class="primarycolor">
					<%} else { %>
				
				<tr class="secondarycolor">
					<%} %>
					<!-- Info -->
					<td align="center">
					<div class="tooltip" id="info<c:out value='${statusreport.id}' />">
					<table>
						<tr>
							<td class="info">id:</td>
							<td class="info" colspan="3"><c:out
								value="${statusreport.id}" /></td>
						</tr>
						<tr>
							<td class="info"><bean:message
								key="main.timereport.tooltip.order" />:</td>
							<td class="info" colspan="3"><c:out
								value="${statusreport.customerorder.sign}" /></td>
						</tr>
						<tr>
							<td class="info">&nbsp;</td>
							<td class="info" colspan="3"><c:out
								value="${statusreport.customerorder.description}" /></td>
						</tr>
						<tr>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.created" />:</td>
							<td class="info"><c:out value="${statusreport.created}" /></td>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.by" /></td>
							<td class="info" valign="top"><c:out
								value="${statusreport.createdby}" /></td>
						</tr>
						<tr>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.edited" />:</td>
							<td class="info"><c:out value="${statusreport.lastupdate}" /></td>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.by" /></td>
							<td class="info" valign="top"><c:out
								value="${statusreport.lastupdatedby}" /></td>
						</tr>
					</table>
					</div>
					<img
						onMouseOver="showWMTT(this,'info<c:out value="${statusreport.id}" />')"
						onMouseOut="hideWMTT()" width="12px" height="12px"
						src="/tb/images/info_button.gif" /></td>
					<!-- order -->
					<td><c:out value="${statusreport.customerorder.sign}" /></td>
					<!-- order description -->
					<td title="${statusreport.customerorder.description}"><c:out
						value="${statusreport.customerorder.shortdescription}" /></td>
					<!-- sort -->
					<td><c:choose>
						<c:when test="${statusreport.sort == 1}">
							<bean:message key="statusreport.sort.periodical" />
						</c:when>
						<c:when test="${statusreport.sort == 2}">
							<bean:message key="statusreport.sort.extra" />
						</c:when>
						<c:when test="${statusreport.sort == 3}">
							<bean:message key="statusreport.sort.final" />
						</c:when>
						<c:otherwise>
							n/a
						</c:otherwise>
					</c:choose></td>
					<!-- status -->
					<td><c:choose>
						<c:when test="${statusreport.overallStatus == 1}">
							<html:img style="width:15px; height:15px;"
								src="/tb/images/green.gif" />
							<bean:message key="statusreport.status.green" />
						</c:when>
						<c:when test="${statusreport.overallStatus == 2}">
							<html:img style="width:15px; height:15px;"
								src="/tb/images/yellow.gif" />
							<bean:message key="statusreport.status.yellow" />
						</c:when>
						<c:when test="${statusreport.overallStatus == 3}">
							<html:img style="width:15px; height:15px;"
								src="/tb/images/red.gif" />
							<bean:message key="statusreport.status.red" />
						</c:when>
						<c:otherwise>
							n/a
						</c:otherwise>
					</c:choose></td>
					<!-- time period -->
					<td align="center"><c:out value="${statusreport.fromdate}" />
					- <c:out value="${statusreport.untildate}" /></td>
					<!-- sender -->
					<td align="center" title="${statusreport.sender.name}"><c:out
						value="${statusreport.sender.sign}" /></td>

					<!-- released -->
					<td align="center" title="${statusreport.released}"><fmt:formatDate
						value="${statusreport.released}" pattern="yyyy-MM-dd HH:mm" />&nbsp;
					</td>

					<!-- recipient -->
					<td align="center" title="${statusreport.recipient.name}"><c:out
						value="${statusreport.recipient.sign}" /></td>

					<!-- accepted -->
					<td align="center" title="${statusreport.accepted}"><fmt:formatDate
						value="${statusreport.accepted}" pattern="yyyy-MM-dd HH:mm" />&nbsp;
					</td>

					<!-- edit, delete -->
					<html:form action="/ShowStatusReport">
						<td align="center" nowrap="nowrap"><html:image
							onclick="editReport(this.form, ${statusreport.id})"
							src="/tb/images/Edit.gif" altKey="statusreport.button.edit.text"
							titleKey="statusreport.button.edit.text" /> &nbsp; <c:if
							test="${loginEmployee.status == 'adm'}">
							<html:image
								onclick="confirmDelete(this.form, ${statusreport.id})"
								src="/tb/images/Delete.gif"
								altKey="statusreport.button.delete.text"
								titleKey="statusreport.button.delete.text" />
						</c:if></td>
					</html:form>
					<% i++; %>
				
			</c:if>
		</c:forEach>
	<tr>
		<html:form action="/CreateStatusReport">
			<td class="noBborderStyle" colspan="4"><html:submit
				styleId="button">
				<bean:message key="statusreport.button.create.text" />
			</html:submit></td>
		</html:form>
	</tr>
</table>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
</body>
</html:html>
