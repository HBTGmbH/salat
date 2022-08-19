<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
<head>

<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.employeeorders.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
<script type="text/javascript" language="JavaScript">

	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/do/DeleteEmployeeorder?eoId=" + id;
			form.submit();
		}
	}
	
	function setUpdateEmployeeOrders(form) {
		form.action = "/do/ShowEmployeeorder";
		form.submit();
	
	}
	
	function refresh(form) {	
		form.action = "/do/ShowEmployeeorder?task=refresh";
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
					<c:if test="${authorizedUser.manager}">
						<html:option value="-1">
							<bean:message key="main.general.allemployees.text" />
						</html:option>
					</c:if>
					<c:forEach var="employeecontract" items="${employeecontracts}">
						<html:option value="${employeecontract.id}">
							<c:out value="${employeecontract.employee.name}" /> |
							<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
								value="${employeecontract.timeString}" />
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
			<c:if test="${employeeordersSize > 10 && (authorizedUser.manager || employeeIsResponsible)}">
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
			<c:if test="${authorizedUser.manager}">
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
			src="/images/info_button.gif" /></td>

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
						test="${employeeorder.debithours == null || employeeorder.debithours.zero}">
							&nbsp;
						</c:when>
					<c:otherwise>
						<java8:formatDuration value="${employeeorder.debithours}" />
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
							<c:when test="${employeeorder.debithours != null && !employeeorder.debithours.zero && employeeorder.debithours.minus(employeeorder.duration).negative}">
								<font color="#FF7777">
									<java8:formatDuration value="${employeeorder.duration}" />
								</font>
							</c:when>
							<c:otherwise>
								<font color="#736F6E">
									<java8:formatDuration value="${employeeorder.duration}" />
								</font>
							</c:otherwise>
						</c:choose>
					</td>
					<td align="right" style="color: gray">
						<c:choose>

							<c:when test="${employeeorder.difference != null && employeeorder.debithoursunit != 0 && employeeorder.debithoursunit != 1 && employeeorder.debithoursunit != 12}">
									<font color="#0000FF"><java8:formatDuration value="${employeeorder.difference}" /></font>
							</c:when>

							<c:when test="${employeeorder.difference != null && employeeorder.difference.negative}">
									<font color="#FF7777"><java8:formatDuration value="${employeeorder.difference}" /></font>
							</c:when>
							<c:when test="${employeeorder.difference != null && !employeeorder.difference.negative}">
									<java8:formatDuration value="${employeeorder.difference}" />
							</c:when>
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
						test="${employeeorder.debithours == null || employeeorder.debithours.zero}">
							&nbsp;
						</c:when>
					<c:otherwise>
					<java8:formatDuration value="${employeeorder.debithours}" />
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
							<c:when test="${employeeorder.debithours != null && !employeeorder.debithours.zero && employeeorder.debithours.minus(employeeorder.duration).negative }">
								<font color="#FF0000">
									<java8:formatDuration value="${employeeorder.duration}" />
								</font>
							</c:when>
							<c:otherwise>
								<java8:formatDuration value="${employeeorder.duration}" />
							</c:otherwise>
						</c:choose>
					</td>
					<td align="right">
						<c:choose>

							<c:when test="${employeeorder.difference != null && employeeorder.debithoursunit != 0 && employeeorder.debithoursunit != 1 && employeeorder.debithoursunit != 12}">
									<font color="#0000FF"><java8:formatDuration value="${employeeorder.difference}" /></font>
							</c:when>

							<c:when test="${employeeorder.difference != null && employeeorder.difference.negative}">
									<font color="#FF0000"><java8:formatDuration value="${employeeorder.difference}" /></font>
							</c:when>
							<c:when test="${employeeorder.difference != null && !employeeorder.difference.negative}">
									<java8:formatDuration value="${employeeorder.difference}" />
							</c:when>
						</c:choose>
					</td>
				</c:if>

			</c:otherwise>
		</c:choose>
		<c:choose>
			<c:when
				test="${authorizedUser.manager || employeeorder.suborder.customerorder.responsible_hbt.id == loginEmployee.id}">
				<td align="center"><html:link
					href="/do/EditEmployeeorder?eoId=${employeeorder.id}">
					<html:img src="/images/Edit.gif" alt="Edit Employeeorder"
						titleKey="main.headlinedescription.employeeorders.edit.text" />
				</html:link></td>
				<html:form action="/DeleteEmployeeorder">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employeeorder.id})"
						src="/images/Delete.gif" alt="Delete Employeeorder"
						titleKey="main.headlinedescription.employeeorders.delete.text" /></td>
				</html:form>
			</c:when>
			<c:otherwise>
				<td align="center"><img height="12px" width="12px"
                                        src="/images/verbot.gif" alt="Edit Employeeorder"
                                        title="<bean:message key="main.headlinedescription.employeeorders.accessdenied.text"/>" /></td>
				<td align="center"><img height="12px" width="12px"
                                        src="/images/verbot.gif" alt="Delete Employeeorder"
                                        title="<bean:message key="main.headlinedescription.employeeorders.accessdenied.text"/>" /></td>
			</c:otherwise>
		</c:choose>
	</c:forEach>
</table>
<c:if test="${authorizedUser.manager || employeeIsResponsible}">
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
</body>
</html:html>
