<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<html:base />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.employeeorders.text" /></title>
<link rel="stylesheet" type="text/css" href="../style/tb.css" />
<link href="../style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="../favicon.ico" />
<script src=""../scripts/"jquery-1.11.3.min.js"></script>
<script src=""../scripts/"select2.full.min.js"></script>
<script type="text/javascript" language="JavaScript">

	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "../do/DeleteEmployeeorder?eoId=" + id;
			form.submit();
		}
	}
	
	function editContent(form, id) {		
		form.action = "../do/EditEmployeeOrderContent?eoId=" + id;
		form.submit();
	}
	
	function setUpdateEmployeeOrders(form) {
		form.action = "../do/ShowEmployeeorder";
		form.submit();
	
	}
	
	function refresh(form) {	
		form.action = "../do/ShowEmployeeorder?task=refresh";
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
<span style="font-size: 14pt; font-weight: bold;">
	<br>
	<bean:message key="main.general.mainmenu.employeeorders.text" />
	<br>
</span>
<br>
<span style="color: red"><html:errors footer="<br>" /> </span>

<html:form action="/ShowEmployeeorder?task=refresh">
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
			<td align="left" class="noBborderStyle">
				<html:select property="employeeContractId" onchange="refresh(this.form)" value="${currentEmployeeContract.id}" styleClass="make-select2">
					<html:option value="-1">
						<bean:message key="main.general.allemployees.text" />
					</html:option>
					<c:forEach var="employeecontract" items="${employeecontracts}">
						<c:if
							test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
							<html:option value="${employeecontract.id}">
								<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
									value="${employeecontract.timeString}" />
								<c:if test="${employeecontract.openEnd}">
									<bean:message key="main.general.open.text" />
								</c:if>)
								</html:option>
						</c:if>
					</c:forEach>
				</html:select>
			</td>
		</tr>
		<!-- select order -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.customerorder.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="orderId" onchange="refresh(this.form)" value="${currentOrderId}" styleClass="make-select2">
					<html:option value="-1">
						<bean:message key="main.general.allorders.text" />
					</html:option>
					<html:options collection="orders" labelProperty="signAndDescription" property="id" />
				</html:select>	
			</td>
		</tr>
		<!-- select suborder -->
		<tr>
	        <td align="left" class="noBborderStyle">
	        	<b><bean:message key="main.employeeorder.suborder.text" />:</b>
	        </td>
			<td align="left" class="noBborderStyle">
	           <html:select property="suborderId"
					value="${currentSub}"
					onchange="refresh(this.form)" styleClass="make-select2">
					<html:option value="-1">
						<bean:message key="main.general.allsuborders.text" />
					</html:option>
					<c:forEach var="suborder" items="${suborders}">
						<html:option value="${suborder.id}">
							${suborder.signAndDescription}
							<c:if test="${!suborder.currentlyValid}">
								&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
							</c:if>
						</html:option>
					</c:forEach>
				</html:select>
				<span style="font-size: 0.6em">
					<bean:message key="main.general.select.expired.text" />
				</span>
			</td>		
		</tr>
		<!-- filter results-->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.general.filter.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:text property="filter" size="40" />
				<html:submit styleId="button" titleKey="main.general.button.filter.alttext.text">
					<bean:message key="main.general.button.filter.text" />
				</html:submit>
			</td>
		</tr>
		<!-- show expired -->
		<tr>
			<td align="left" valign="top" class="noBborderStyle">
				<b><bean:message key="main.general.showexpired.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:checkbox property="show" onclick="refresh(this.form)" />
			</td>
		</tr>
		<!-- show actual hours -->
		<tr>
			<td align="left" valign="top" class="noBborderStyle">
				<b><bean:message key="main.general.showactualhoursflag.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:checkbox property="showActualHours" onclick="refresh(this.form)" />
			</td>
		</tr>
	</table>
</html:form>

<table>
	<tbody>
		<tr>
			<bean:size id="employeeordersSize" name="employeeorders" />
			<c:if test="${employeeordersSize > 10 && (employeeAuthorized || employeeIsResponsible)}">
				<td class="noBborderStyle" >
					<html:form action="/CreateEmployeeorder">
							<html:submit styleId="button" titleKey="main.general.button.createemployeeorder.alttext.text">
								<bean:message key="main.general.button.createemployeeorder.text" />
							</html:submit>
					</html:form>
				</td>
				<td class="noBborderStyle">
					<html:form action="/GenerateMultipleEmployeeorders?task=initialize">
						<html:submit styleId="button" titleKey="main.general.button.generatemultipleemployeeorders.alttext.text">
							<bean:message key="main.general.button.generatemultipleemployeeorders.text" />
						</html:submit>
					</html:form>
				</td>
			</c:if>
			<c:if test="${employeeAuthorized}">
				<td class="noBborderStyle"> 
					<html:form action="/ShowEmployeeorder?task=adjustDates">
						<html:submit styleId="button" titleKey="main.general.button.adjustemployeeorder.alttext.text">
							<bean:message key="main.general.button.adjustemployeeorder.text" />
						</html:submit>
					</html:form>
				</td>			
			</c:if>
		</tr>
	</tbody>
</table>

<table class="center backgroundcolor">
	<tr>
		<th align="left" title="Info"><b>Info</b></th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.employeename.text' />">
			<b><bean:message key="main.employeeorder.employee.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.ordernumber.text' />" colspan="1">
			<b><bean:message key="main.employeeorder.customerorder.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.subordernumber.text' />">
			<b><bean:message key="main.employeeorder.suborder.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.suborders.description.text' />"	colspan="1">
			<b><bean:message key="main.headlinedescription.suborders.description.text" /></b>
		</th>
        <th align="left" title="<bean:message key='main.headlinedescription.suborders.description.text' />" colspan="1">
        	<b><bean:message key="main.headlinedescription.suborders.suborderdescription.text" /></b>
        </th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.validfrom.text' />">
			<b><bean:message key="main.employeeorder.validfrom.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.validuntil.text' />">
			<b><bean:message key="main.employeeorder.validuntil.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.debit.text' />">
			<b><bean:message key="main.employeeorder.debithours.text" /></b>
		</th>
		<c:if test="${showActualHours}">
			<th align="left">
				<b><bean:message key="main.general.showactualhours.text" /></b>
			</th>
			<th align="left">
				<b><bean:message key="main.general.difference.text"/></b>
			</th>
		</c:if>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.content.text' />">
			<b><bean:message key="main.employeeorder.content.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.edit.text' />">
			<b><bean:message key="main.employeeorder.edit.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.delete.text' />">
			<b><bean:message key="main.employeeorder.delete.text" /></b>
		</th>
	</tr>
	<c:forEach var="employeeorder" items="${employeeorders}" varStatus="statusID">
		<c:choose>
			<c:when test="${statusID.count % 2 == 0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>

		<!-- Info -->
		<td align="center">
		<div class="tooltip" id="info<c:out value='${employeeorder.id}' />">
		<table>
			<tr>
				<td class="info">id:</td>
				<td class="info" colspan="3"><c:out value="${employeeorder.id}" /></td>
			</tr>
			<tr>
				<td class="info"><bean:message
					key="main.timereport.tooltip.employee" />:</td>
				<td class="info" colspan="3"><c:out
					value="${employeeorder.employeecontract.employee.name}" />&nbsp;&nbsp;(<c:out
					value="${employeeorder.employeecontract.timeString}" /><c:if
					test="${employeeorder.employeecontract.openEnd}">
					<bean:message key="main.general.open.text" />
				</c:if>)</td>
			</tr>
			<tr>
				<td class="info"><bean:message
					key="main.timereport.tooltip.order" />:</td>
				<td class="info" colspan="3"><c:out
					value="${employeeorder.suborder.customerorder.sign}" /></td>
			</tr>
			<tr>
				<td class="info">&nbsp;</td>
				<td class="info" colspan="3"><c:out
					value="${employeeorder.suborder.customerorder.description}" /></td>
			</tr>
			<tr>
				<td class="info">&nbsp;</td>
				<td class="info" colspan="3">(<c:out
					value="${employeeorder.fromDate}" /> - <c:if
					test="${employeeorder.untilDate!=null}">
					<c:out value="${employeeorder.untilDate}" />
				</c:if><c:if test="${employeeorder.untilDate==null}">
					<bean:message key="main.general.open.text" />
				</c:if>)</td>
			</tr>
			<tr>
				<td class="info"><bean:message
					key="main.timereport.tooltip.suborder" />:</td>
				<td class="info" colspan="3"><c:out
					value="${employeeorder.suborder.sign}" /></td>
			</tr>
			<tr>
				<td class="info">&nbsp;</td>
				<td class="info" colspan="3"><c:out
					value="${employeeorder.suborder.description}" /></td>
			</tr>
			<tr>
				<td class="info">&nbsp;</td>
				<td class="info" colspan="3">(<c:out
					value="${employeeorder.suborder.fromDate}" /> - <c:if
					test="${employeeorder.suborder.untilDate!=null}">
					<c:out value="${employeeorder.suborder.untilDate}" />
				</c:if><c:if test="${employeeorder.suborder.untilDate==null}">
					<bean:message key="main.general.open.text" />
				</c:if>)</td>
			</tr>
			<tr>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.created" />:</td>
				<td class="info"><c:out value="${employeeorder.created}" /></td>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.by" /></td>
				<td class="info" valign="top"><c:out
					value="${employeeorder.createdby}" /></td>
			</tr>
			<tr>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.edited" />:</td>
				<td class="info"><c:out value="${employeeorder.lastupdate}" /></td>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.by" /></td>
				<td class="info" valign="top"><c:out
					value="${employeeorder.lastupdatedby}" /></td>
			</tr>
		</table>

		</div>
		<img
			onMouseOver="showWMTT(this,'info<c:out value="${employeeorder.id}" />')"
			onMouseOut="hideWMTT()" width="12px" height="12px"
			src="../images/info_button.gif" /></td>

		<c:choose>
			<c:when test="${!employeeorder.currentlyValid}">
				<td style="color: gray"
					title="<c:out value="${employeeorder.employeecontract.employee.name}" />&nbsp;&nbsp;(<c:out value="${employeeorder.employeecontract.timeString}" /><c:if
					test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)"><c:out
					value="${employeeorder.employeecontract.employee.sign}" /></td>
				<td style="color: gray"
					title="<c:out value="${employeeorder.suborder.customerorder.description}" />"><c:out
					value="${employeeorder.suborder.customerorder.sign}" /></td>
				<td style="color: gray"
					title="<c:out value="${employeeorder.suborder.description}" />"><c:out
					value="${employeeorder.suborder.sign}" /></td>
				<td style="color: gray"><c:out
					value="${employeeorder.suborder.customerorder.shortdescription}" /></td>
				<td style="color: gray"><c:out
					value="${employeeorder.suborder.shortdescription}" /></td>

				<c:choose>
					<c:when test="${!employeeorder.fitsToSuperiorObjects}">
						<td style="color: red"><c:out
							value="${employeeorder.fromDate}" /></td>
						<td style="color: red"><c:choose>
							<c:when test="${employeeorder.untilDate == null}">
								<bean:message key="main.general.open.text" />
							</c:when>
							<c:otherwise>
								<c:out value="${employeeorder.untilDate}" />
							</c:otherwise>
						</c:choose></td>
					</c:when>
					<c:otherwise>
						<td style="color: gray"><c:out
							value="${employeeorder.fromDate}" /></td>
						<td style="color: gray"><c:choose>
							<c:when test="${employeeorder.untilDate == null}">
								<bean:message key="main.general.open.text" />
							</c:when>
							<c:otherwise>
								<c:out value="${employeeorder.untilDate}" />
							</c:otherwise>
						</c:choose></td>
					</c:otherwise>
				</c:choose>

				<td style="color: gray"><c:choose>
					<c:when
						test="${employeeorder.debithours == null || employeeorder.debithours == 0.0}">
							&nbsp;
						</c:when>
					<c:otherwise>
						<fmt:formatNumber value="${employeeorder.debithours}"  minFractionDigits="2"/>
						<c:choose>
							<c:when test="${employeeorder.debithoursunit == 0}">
									/ <bean:message key="main.general.totaltime.text" />
							</c:when>
							<c:when test="${employeeorder.debithoursunit == 1}">
									/ <bean:message key="main.general.year.text" />
							</c:when>
							<c:when test="${employeeorder.debithoursunit == 12}">
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
						<c:choose>
							<c:when test="${employeeorder.debithours != null && employeeorder.debithours != 0.0 && employeeorder.duration > employeeorder.debithours}">
								<font color="#FF7777">
									<fmt:formatNumber value="${employeeorder.duration}"  minFractionDigits="2"/>
								</font>
							</c:when>
							<c:otherwise>
								<font color="#736F6E">
									<fmt:formatNumber value="${employeeorder.duration}"  minFractionDigits="2"/>
								</font>
							</c:otherwise>
						</c:choose>
					</td>
					<td align="right" style="color: gray">
						<c:choose>

							<c:when test="${employeeorder.difference != null && (employeeorder.difference < 0.0 || employeeorder.difference >= 0.0)&&(employeeorder.debithoursunit != 0 && employeeorder.debithoursunit != 1 && employeeorder.debithoursunit != 12)}">
									<font color="#0000FF"><fmt:formatNumber value="${employeeorder.difference}" minFractionDigits="2"/></font>
							</c:when>

							<c:when test="${employeeorder.difference != null && employeeorder.difference < 0.0}">
									<font color="#FF7777"><fmt:formatNumber value="${employeeorder.difference}" minFractionDigits="2"/></font>
							</c:when>
							<c:when test="${employeeorder.difference != null && employeeorder.difference >= 0.0}">
									<fmt:formatNumber value="${employeeorder.difference}" minFractionDigits="2"/>
							</c:when>
							<c:otherwise>
								&nbsp;
							</c:otherwise>
						</c:choose>
					</td>
				</c:if>

			</c:when>
			<c:otherwise>
				<td
					title="<c:out value="${employeeorder.employeecontract.employee.name}" />&nbsp;&nbsp;(<c:out value="${employeeorder.employeecontract.timeString}" /><c:if
					test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)"><c:out
					value="${employeeorder.employeecontract.employee.sign}" /></td>
				<td
					title="<c:out value="${employeeorder.suborder.customerorder.description}" />"><c:out
					value="${employeeorder.suborder.customerorder.sign}" /></td>
				<!-- td><c:out value="${employeeorder.suborder.customerorder.shortdescription}" /></td -->
				<td title="<c:out value="${employeeorder.suborder.description}" />"><c:out
					value="${employeeorder.suborder.sign}" /></td>

				<td><c:out
					value="${employeeorder.suborder.customerorder.shortdescription}" /></td>
				<td><c:out value="${employeeorder.suborder.shortdescription}" /></td>

				<c:choose>
					<c:when test="${!employeeorder.fitsToSuperiorObjects}">
						<td style="color: red"><c:out
							value="${employeeorder.fromDate}" /></td>
						<td style="color: red"><c:choose>
							<c:when test="${employeeorder.untilDate == null}">
								<bean:message key="main.general.open.text" />
							</c:when>
							<c:otherwise>
								<c:out value="${employeeorder.untilDate}" />
							</c:otherwise>
						</c:choose></td>
					</c:when>
					<c:otherwise>
						<td><c:out value="${employeeorder.fromDate}" /></td>
						<td><c:choose>
							<c:when test="${employeeorder.untilDate == null}">
								<bean:message key="main.general.open.text" />
							</c:when>
							<c:otherwise>
								<c:out value="${employeeorder.untilDate}" />
							</c:otherwise>
						</c:choose></td>
					</c:otherwise>
				</c:choose>

				<td><c:choose>
					<c:when
						test="${employeeorder.debithours == null || employeeorder.debithours == 0.0}">
							&nbsp;
						</c:when>
					<c:otherwise>
					<fmt:formatNumber value="${employeeorder.debithours}"  minFractionDigits="2"/>
						<c:choose>
							<c:when test="${employeeorder.debithoursunit == 0}">
									/ <bean:message key="main.general.totaltime.text" />
							</c:when>
							<c:when test="${employeeorder.debithoursunit == 1}">
									/ <bean:message key="main.general.year.text" />
							</c:when>
							<c:when test="${employeeorder.debithoursunit == 12}">
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
						<c:choose>
							<c:when test="${employeeorder.debithours != null && employeeorder.debithours != 0.0 && employeeorder.duration > employeeorder.debithours}">
								<font color="#FF0000">
									<fmt:formatNumber value="${employeeorder.duration}"  minFractionDigits="2"/>
								</font>
							</c:when>
							<c:otherwise>
								<fmt:formatNumber value="${employeeorder.duration}"  minFractionDigits="2"/>
							</c:otherwise>
						</c:choose>
					</td>
					<td align="right">
						<c:choose>

							<c:when test="${employeeorder.difference != null && (employeeorder.difference < 0.0 || employeeorder.difference >= 0.0)&&(employeeorder.debithoursunit != 0 && employeeorder.debithoursunit != 1 && employeeorder.debithoursunit != 12)}">
									<font color="#0000FF"><fmt:formatNumber value="${employeeorder.difference}" minFractionDigits="2"/></font>
							</c:when>

							<c:when test="${employeeorder.difference != null && employeeorder.difference < 0.0}">
									<font color="#FF0000"><fmt:formatNumber value="${employeeorder.difference}" minFractionDigits="2"/></font>
							</c:when>
							<c:when test="${employeeorder.difference != null && employeeorder.difference >= 0.0}">
									<fmt:formatNumber value="${employeeorder.difference}" minFractionDigits="2"/>
							</c:when>
							<c:otherwise>
								&nbsp;
							</c:otherwise>
						</c:choose>
					</td>
				</c:if>

			</c:otherwise>
		</c:choose>
			<td align="center" valign="middle">
				<c:choose>
					<c:when test="${!loginEmployeeContract.freelancer && !employeeorder.suborder.noEmployeeOrderContent}">
						<html:form action="/EditEmployeeOrderContent" style="margin: 0">
							<c:choose>
								<c:when test="${employeeorder.employeeOrderContent == null || (employeeorder.employeeOrderContent.committed_mgmt != true && employeeorder.employeeOrderContent.committed_emp != true)}">
									<html:image onclick="editContent(this.form, ${employeeorder.id})"
										src="../images/thumb_down.gif" titleKey="employeeordercontent.thumbdown.text" />
								</c:when>
								<c:when
									test="${employeeorder.employeeOrderContent != null && (employeeorder.employeeOrderContent.committed_mgmt != true || employeeorder.employeeOrderContent.committed_emp != true)}">
									<html:image onclick="editContent(this.form, ${employeeorder.id})"
										src="../images/yellow.gif" titleKey="employeeordercontent.yellow.text" />
								</c:when>
								<c:otherwise>
									<html:image onclick="editContent(this.form, ${employeeorder.id})"
										src="../images/thumb_up.gif" titleKey="employeeordercontent.thumbup.text" />
								</c:otherwise>
							</c:choose>
						</html:form>
					</c:when>
					<c:otherwise>&nbsp;</c:otherwise>
				</c:choose>
			</td>
		<c:choose>
			<c:when
				test="${employeeAuthorized || employeeorder.suborder.customerorder.responsible_hbt.id == loginEmployee.id}">
				<td align="center"><html:link
					href="../do/EditEmployeeorder?eoId=${employeeorder.id}">
					<html:img src="../images/Edit.gif" alt="Edit Employeeorder"
						titleKey="main.headlinedescription.employeeorders.edit.text" />
				</html:link></td>
				<html:form action="/DeleteEmployeeorder">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employeeorder.id})"
						src="../images/Delete.gif" alt="Delete Employeeorder"
						titleKey="main.headlinedescription.employeeorders.delete.text" /></td>
				</html:form>
			</c:when>
			<c:otherwise>
				<td align="center"><img height="12px" width="12px"
                                        src="../images/verbot.gif" alt="Edit Employeeorder"
                                        title="<bean:message key="main.headlinedescription.employeeorders.accessdenied.text"/>" /></td>
				<td align="center"><img height="12px" width="12px"
                                        src="../images/verbot.gif" alt="Delete Employeeorder"
                                        title="<bean:message key="main.headlinedescription.employeeorders.accessdenied.text"/>" /></td>
			</c:otherwise>
		</c:choose>
	</c:forEach>
</table>
<c:if test="${employeeAuthorized || employeeIsResponsible}">
	<table>
		<tbody>
			<tr>
				<td class="noBborderStyle">
					<html:form action="/CreateEmployeeorder">
						<html:submit styleId="button" titleKey="main.general.button.createemployeeorder.alttext.text">
							<bean:message key="main.general.button.createemployeeorder.text" />
						</html:submit>
					</html:form>
				</td>
				<td class="noBborderStyle">
					<html:form action="/GenerateMultipleEmployeeorders?task=initialize">
						<html:submit styleId="button" titleKey="main.general.button.generatemultipleemployeeorders.alttext.text">
							<bean:message key="main.general.button.generatemultipleemployeeorders.text" />
						</html:submit>
					</html:form>
				</td>
			</tr>
		</tbody>
	</table>
</c:if>
<% request.getSession().setAttribute("addEmployeeOrderContentVisited", true); %>
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
