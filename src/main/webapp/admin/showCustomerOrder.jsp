<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.customerorders.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteCustomerorder?coId=" + id;
			form.submit();
		}
	}	
	
	function refresh(form) {	
		form.action = "/tb/do/ShowCustomerorder?task=refresh";
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
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br>
<bean:message key="main.general.mainmenu.customerorders.text" /><br>
</span>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="center backgroundcolor">
	<html:form action="/ShowCustomerorder?task=refresh">
		<tr>
			<td class="noBborderStyle" colspan="2"><b><bean:message
				key="main.customerorder.customer.text" /></b></td>
			<td class="noBborderStyle" colspan="9" align="left"><html:select
				property="customerId" onchange="refresh(this.form)">
				<html:option value="-1">
					<bean:message key="main.general.allcustomers.text" />
				</html:option>
				<html:options collection="customers" labelProperty="shortname"
					property="id" />
			</html:select></td>
		</tr>
		<tr>
			<td class="noBborderStyle" colspan="2"><b><bean:message
				key="main.general.filter.text" /></b></td>
			<td class="noBborderStyle" colspan="9" align="left"><html:text
				property="filter" size="40" /> <html:submit styleId="button"
				titleKey="main.general.button.filter.alttext.text">
				<bean:message key="main.general.button.filter.text" />
			</html:submit></td>
		</tr>
		<tr>
			<td class="noBborderStyle" colspan="2"><b><bean:message
				key="main.general.showexpired.text" /></b></td>
			<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
				property="show" onclick="refresh(this.form)" /></td>
		</tr>
		<tr>
			<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.showactualhoursflag.text" /></b></td>
			<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
					property="showActualHours" onclick="refresh(this.form)" /></td>
		</tr>
	</html:form>

	<bean:size id="customerordersSize" name="customerorders" />
	<c:if test="${customerordersSize>10}">
		<c:if test="${employeeAuthorized}">
			<tr>
				<html:form action="/CreateCustomerorder">
					<td class="noBborderStyle" colspan="4"><html:submit
						styleId="button"
						titleKey="main.general.button.createcustomerorder.alttext.text">
						<bean:message key="main.general.button.createcustomerorder.text" />
					</html:submit></td>
				</html:form>
			</tr>
		</c:if>
	</c:if>
	<tr>
		<th align="left"
			title="Info"><b>Info</b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.customer.text" />"><b><bean:message
			key="main.customerorder.customer.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.ordernumber.text" /> - <bean:message
			key="main.headlinedescription.orders.description.text" />"><b><bean:message
			key="main.customerorder.order.text" /><br><bean:message
			key="main.customerorder.shortdescription.short.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.validfrom.text" /> - <bean:message
			key="main.headlinedescription.orders.validuntil.text" />"><b><bean:message
			key="main.customerorder.validfrom.text" /><br><bean:message
			key="main.customerorder.validuntil.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.responsecustomer.text" />"><b><bean:message
			key="main.customerorder.responsiblecustomer.text" /><br><bean:message
			key="main.customerorder.tech.text" /><br><bean:message
			key="main.customerorder.contract.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.responsehbt.text" />"><b><bean:message
			key="main.customerorder.table.responsiblehbt.text" /><br><bean:message
			key="main.customerorder.dv.text" /><br><bean:message
			key="main.customerorder.av.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.customerorder.statusreport.text" /></b></th>				
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.ordercustomer.text" />"><b><bean:message
			key="main.customerorder.ordercustomer.text" /></b></th>
		<!--  
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.currency.text" />"><b><bean:message
			key="main.customerorder.currency.text" /></b></th>
		-->
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.orders.hourlyrate.text" />"><b><bean:message
			key="main.customerorder.hourlyrate.text" /></b></th>
		<th align="left"><b><bean:message
			key="main.general.debithours.text" /></b></th>
		<c:if test="${showActualHours}">
			<th align="left"><b><bean:message 
				key="main.general.showactualhours.text" /></b></th>
			<th align="left"><b><bean:message
				key="main.general.difference.text"/></b></th></c:if>	
		<c:if test="${employeeAuthorized}">
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.orders.edit.text" />"><b><bean:message
				key="main.customerorder.edit.text" /></b></th>
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.orders.delete.text" />"><b><bean:message
				key="main.customerorder.delete.text" /></b></th>
		</c:if>
	</tr>
	<c:forEach var="customerorder" items="${customerorders}"
		varStatus="statusID">

		<c:choose>
			<c:when test="${statusID.count%2==0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>

				<!-- Info -->
				<td align="center">
				<div class="tooltip" id="info<c:out value='${customerorder.id}' />">
				<table>
					<tr>
						<td class="info">id:</td>
						<td class="info" colspan="3"><c:out value="${customerorder.id}" /></td>
					</tr>
					<tr>
						<td class="info"><bean:message
							key="main.customerorder.customer.text" />:</td>
						<td class="info" colspan="3"><c:out
							value="${customerorder.customer.name}" /></td>
					</tr>
					<tr>
						<td class="info"><bean:message
							key="main.timereport.tooltip.order" />:</td>
						<td class="info" colspan="3"><c:out
							value="${customerorder.sign}" /></td>
					</tr>
					<tr>						
						<td class="info"><bean:message
							key="main.customerorder.jiraIDs.text"/>:</td>
						<td class="info" colspan="3">
							<c:forEach var="projectID" items="${customerorder.projectIDs}">
							<c:out value="${projectID.jiraProjectID}"/>&nbsp;</c:forEach>
						</td>
					</tr> 
 
					
					<tr>
						<td class="info">&nbsp;</td>
						<td class="info" colspan="3"><c:out
							value="${customerorder.description}" /></td>
					</tr>
					<tr>
						<td class="info" valign="top"><bean:message
							key="main.timereport.tooltip.created" />:</td>
						<td class="info"><c:out value="${customerorder.created}" /></td>
						<td class="info" valign="top"><bean:message
							key="main.timereport.tooltip.by" /></td>
						<td class="info" valign="top"><c:out
							value="${customerorder.createdby}" /></td>
					</tr>
					<tr>
						<td class="info" valign="top"><bean:message
							key="main.timereport.tooltip.edited" />:</td>
						<td class="info"><c:out value="${customerorder.lastupdate}" /></td>
						<td class="info" valign="top"><bean:message
							key="main.timereport.tooltip.by" /></td>
						<td class="info" valign="top"><c:out
							value="${customerorder.lastupdatedby}" /></td>
					</tr>
					<tr>
						<td class="info" valign="top"><bean:message
							key="main.general.hide" />:</td>
						<td class="info"><c:choose><c:when test="${customerorder.hide == true}"><bean:message
							key="main.general.yes" /></c:when><c:otherwise><bean:message
							key="main.general.no" /></c:otherwise></c:choose></td>
					</tr>
				</table>

				</div>
				<img
					onMouseOver="showWMTT(this,'info<c:out value="${customerorder.id}" />')"
					onMouseOut="hideWMTT()" width="12px" height="12px"
					src="/tb/images/info_button.gif" /></td>

		<!-- invalid orders should be gray -->
		<c:choose>
			<c:when test="${customerorder.currentlyValid}">
				<td><c:out value="${customerorder.customer.shortname}" /></td>
				<td nowrap="nowrap"><c:out value="${customerorder.sign}" /><br><c:out value="${customerorder.shortdescription}" /></td>				
				<td nowrap="nowrap">
					<c:out value="${customerorder.fromDate}" />
					<br>
					<c:choose>
						<c:when test="${customerorder.untilDate == null}">
							<bean:message key="main.general.open.text" />
						</c:when>
						<c:otherwise>
							<c:out value="${customerorder.untilDate}" />
						</c:otherwise>
					</c:choose>
				</td>
				<td nowrap="nowrap">
					a) <c:out value="${customerorder.responsible_customer_technical}" />
					<br>
					b) <c:out value="${customerorder.responsible_customer_contractually}" />
				</td>
				<td nowrap="nowrap">
					a) <c:out value="${customerorder.responsible_hbt.name}" />
					<br>
					b) <c:out value="${customerorder.respEmpHbtContract.name}" />
				</td>
				<td align="center">
					<c:choose>
						<c:when test="${customerorder.statusreport == 0}">
							-
						</c:when>
						<c:when test="${customerorder.statusreport == 12}">
							<bean:message key="main.customerorder.statusreport.option.12.text" />
						</c:when>
						<c:when test="${customerorder.statusreport == 6}">
							<bean:message key="main.customerorder.statusreport.option.6.text" />
						</c:when>
						<c:when test="${customerorder.statusreport == 4}">
							<bean:message key="main.customerorder.statusreport.option.4.text" />
						</c:when>
					</c:choose>
				</td>
				<td><c:out value="${customerorder.order_customer}" /></td>
				<td>
					<c:choose>
						<c:when test="${customerorder.hourly_rate == 0.0}">
							&nbsp;
						</c:when>
						<c:otherwise>
							<fmt:formatNumber value="${customerorder.hourly_rate}" minFractionDigits="2"/>&nbsp;<c:out value="${customerorder.currency}" />
						</c:otherwise>
					</c:choose>
				</td>				
				
				<td><c:choose>
					<c:when test="${customerorder.debithours == null}">
							&nbsp;
						</c:when>
					<c:otherwise>
						<fmt:formatNumber value="${customerorder.debithours}"  minFractionDigits="2"/>
						<c:choose>
							<c:when test="${customerorder.debithoursunit == 0}">
									/ <bean:message key="main.general.totaltime.text" />
							</c:when>
							<c:when test="${customerorder.debithoursunit == 1}">
									/ <bean:message key="main.general.year.text" />
							</c:when>
							<c:when test="${customerorder.debithoursunit == 12}">
									/ <bean:message key="main.general.month.text" />
							</c:when>
							<c:otherwise>
									?
								</c:otherwise>
						</c:choose>
					</c:otherwise>
				</c:choose></td>
				
				<c:if test="${showActualHours}">
					<td align="right">
						<fmt:formatNumber value="${customerorder.duration}"  minFractionDigits="2"/>
					</td>
						<td align="right">
							<c:choose>
								<c:when test="${customerorder.difference != null && (customerorder.difference < 0.0 || customerorder.difference >= 0.0)&&(customerorder.debithoursunit != 0 && customerorder.debithoursunit != 1 && customerorder.debithoursunit != 12)}">
										<font color="#0000FF"><fmt:formatNumber value="${customerorder.difference}" minFractionDigits="2"/></font>
								</c:when>						
								<c:when test="${customerorder.difference != null && customerorder.difference < 0.0}">
										<font color="#FF7777"><fmt:formatNumber value="${customerorder.difference}" minFractionDigits="2"/></font>
								</c:when>
								<c:when test="${customerorder.difference != null && customerorder.difference >= 0.0}">
										<fmt:formatNumber value="${customerorder.difference}" minFractionDigits="2"/>
								</c:when>
								<c:otherwise>
									&nbsp;
								</c:otherwise>
							</c:choose>
						</td>						
				</c:if>
				
			</c:when>
			<c:otherwise>
				<!-- customerorder is invalid -->
				<td style="color:gray"><c:out
					value="${customerorder.customer.shortname}" /></td>
				<td style="color:gray" nowrap="nowrap"><c:out value="${customerorder.sign}" /><br><c:out
					value="${customerorder.shortdescription}" /></td>
				<td style="color:gray" nowrap="nowrap">
					<c:out value="${customerorder.fromDate}" />
					<br>
					<c:choose>
						<c:when test="${customerorder.untilDate == null}">
							<bean:message key="main.general.open.text" />
						</c:when>
						<c:otherwise>
							<c:out value="${customerorder.untilDate}" />
						</c:otherwise>
					</c:choose>
				</td>
				<td nowrap="nowrap" style="color:gray">
					a) <c:out value="${customerorder.responsible_customer_contractually}" />
					<br>
					b) <c:out value="${customerorder.responsible_customer_technical}" />
				</td>
				<td nowrap="nowrap" style="color:gray">
					a) <c:out value="${customerorder.responsible_hbt.name}" />
					<br>
					b) <c:out value="${customerorder.respEmpHbtContract.name}" />
				</td>
				<td style="color:gray" align="center">
					<c:choose>
						<c:when test="${customerorder.statusreport == 0}">
							-
						</c:when>
						<c:when test="${customerorder.statusreport == 12}">
							<bean:message key="main.customerorder.statusreport.option.12.text" />
						</c:when>
						<c:when test="${customerorder.statusreport == 6}">
							<bean:message key="main.customerorder.statusreport.option.6.text" />
						</c:when>
						<c:when test="${customerorder.statusreport == 4}">
							<bean:message key="main.customerorder.statusreport.option.4.text" />
						</c:when>
					</c:choose>
				</td>
				<td style="color:gray"><c:out
					value="${customerorder.order_customer}" /></td>
				<td style="color:gray">
					<c:choose>
						<c:when test="${customerorder.hourly_rate == 0.0}">
							&nbsp;
						</c:when>
						<c:otherwise>
							<c:out value="${customerorder.hourly_rate}" />&nbsp;<c:out value="${customerorder.currency}" />
						</c:otherwise>
					</c:choose>
				</td>	
				<td style="color:gray">
					<c:choose>
						<c:when test="${customerorder.debithours == null}">
							&nbsp;
						</c:when>
					<c:otherwise>
						<fmt:formatNumber value="${customerorder.debithours}"  minFractionDigits="2"/>
						<c:choose>
							<c:when test="${customerorder.debithoursunit == 0}">
									/ <bean:message key="main.general.totaltime.text" />
							</c:when>
							<c:when test="${customerorder.debithoursunit == 1}">
									/ <bean:message key="main.general.year.text" />
							</c:when>
							<c:when test="${customerorder.debithoursunit == 12}">
									/ <bean:message key="main.general.month.text" />
							</c:when>
							<c:otherwise>
									?
								</c:otherwise>
						</c:choose>
					</c:otherwise>
				</c:choose></td>
				
				<c:if test="${showActualHours}">
					<td  align="right" style="color:gray">
						<fmt:formatNumber value="${customerorder.duration}"  minFractionDigits="2"/>
					</td>
						<td align="right" style="color:gray">
							<c:choose>
								<c:when test="${customerorder.difference != null && (customerorder.difference < 0.0 || customerorder.difference >= 0.0)&&(customerorder.debithoursunit != 0 && customerorder.debithoursunit != 1 && customerorder.debithoursunit != 12)}">
										<font color="#0000FF"><fmt:formatNumber value="${customerorder.difference}" minFractionDigits="2"/></font>
								</c:when>						
								<c:when test="${customerorder.difference != null && customerorder.difference < 0.0}">
										<font color="#FF0000"><fmt:formatNumber value="${suborder.difference}" minFractionDigits="2"/></font>
								</c:when>
								<c:when test="${customerorder.difference != null && customerorder.difference >= 0.0}">
										<fmt:formatNumber value="${customerorder.difference}" minFractionDigits="2"/>
								</c:when>
								<c:otherwise>
									&nbsp;
								</c:otherwise>
							</c:choose>
						</td>	
				</c:if>
				
			</c:otherwise>
		</c:choose>


		<c:if test="${employeeAuthorized}">
			<td align="center"><html:link
				href="/tb/do/EditCustomerorder?coId=${customerorder.id}">
				<img src="/tb/images/Edit.gif" alt="Edit Customerorder"
					title="<bean:message key="main.headlinedescription.orders.edit.text"/>" />
			</html:link></td>
			<html:form action="/DeleteCustomerorder">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${customerorder.id})"
					src="/tb/images/Delete.gif" alt="Delete Customerorder"
					titleKey="main.headlinedescription.orders.edit.text" /></td>
			</html:form>
		</c:if>
		</tr>

	</c:forEach>
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateCustomerorder">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button"
					titleKey="main.general.button.createcustomerorder.alttext.text">
					<bean:message key="main.general.button.createcustomerorder.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
</body>
<br><br><br><br><br><br><br>
</html:html>
