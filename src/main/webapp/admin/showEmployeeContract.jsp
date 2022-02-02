<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">


<html:html>
<head>
<html:base />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.employeecontracts.text" /></title>
<link href="../style/select2.min.css" rel="stylesheet" />
<script src=""../scripts/"jquery-1.11.3.min.js"></script>
<script src=""../scripts/"select2.full.min.js"></script>
<link rel="stylesheet" type="text/css" href="../style/tb.css" />
<link rel="shortcut icon" type="image/x-icon" href="../favicon.ico" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "../do/DeleteEmployeecontract?ecId=" + id;
			form.submit();
		}
	}
	
	function refresh(form) {	
		form.action = "../do/ShowEmployeecontract?task=refresh";
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
<span style="font-size:14pt;font-weight:bold;"><br><bean:message
	key="main.general.mainmenu.employeecontracts.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="center backgroundcolor">
<html:form action="/ShowEmployeecontract?task=refresh">
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message
			key="main.employeecontract.employee.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left">
			<html:select property="employeeId" onchange="refresh(this.form)" styleClass="make-select2">
				<html:option value="-1">
					<bean:message key="main.general.allemployees.text" />
				</html:option>
				<c:forEach var="employee" items="${employees}">
					<c:if test="${employee.sign != 'adm'}">
						<html:option value="${employee.id}">
							<c:out value="${employee.name}" />
						</html:option>
					</c:if>
				</c:forEach>				
			</html:select>
		</td>
	</tr>
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.filter.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left">
			<html:text property="filter" size="40" />
			<html:submit styleId="button" titleKey="main.general.button.filter.alttext.text">
				<bean:message key="main.general.button.filter.text" />
			</html:submit>
		</td>
	</tr>
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.showexpired.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left"><html:checkbox
				property="show" onclick="refresh(this.form)" /> </td>
	</tr>
</html:form>

	<bean:size id="employeecontractsSize" name="employeecontracts" />
	<c:if test="${employeecontractsSize>10}">
		<c:if test="${employeeAuthorized}">
			<tr>
				<html:form action="/CreateEmployeecontract">
					<td class="noBborderStyle" colspan="4"><html:submit
						styleId="button"
						titleKey="main.general.button.createemployeecontract.alttext.text">
						<bean:message
							key="main.general.button.createemployeecontract.text" />
					</html:submit></td>
				</html:form>
			</tr>
		</c:if>
	</c:if>
	<tr>
		<th align="left"
			title="Info"><b>Info</b></th>
		<th align="left"
			title="<bean:message key="main.headlinedescription.employeecontracts.employeename.text" />"><b><bean:message
			key="main.employeecontract.employee.text" /></b></th>
		<th align="left"
			title="<bean:message key="main.headlinedescription.employeecontracts.taskdescription.text" />"><b><bean:message
			key="main.employeecontract.taskdescription.text" /></b></th>
		<th align="left"
			title="<bean:message key="main.headlinedescription.employeecontracts.headofdepartment.text" />"><b><bean:message
			key="main.employeecontract.supervisor.text" /></b></th>
		<th align="left"
			title="<bean:message key="main.headlinedescription.employeecontracts.validfrom.text" />"><b><bean:message
			key="main.employeecontract.validfrom.text" /></b></th>
		<th align="left"
			title="<bean:message key="main.headlinedescription.employeecontracts.validuntil.text" />"><b><bean:message
			key="main.employeecontract.validuntil.text" /></b></th>
		<th align="center"
			title="<bean:message key="main.headlinedescription.employeecontracts.freelancer.text" />"><b><bean:message
			key="main.employeecontract.freelancer.text" /></b></th>
		<th align="center"
			title="<bean:message key="main.headlinedescription.employeecontracts.dailyworkingtime.text" />"><b><bean:message
			key="main.employeecontract.dailyworkingtime.text" /></b></th>
		<th align="center"
			title="<bean:message key="main.headlinedescription.employeecontracts.vacationdaysperyear.text" />"><b><bean:message
			key="main.employeecontract.yearlyvacation.text" /></b></th>
		<c:if test="${employeeAuthorized}">
			<th align="left"
				title="<bean:message key="main.headlinedescription.employeecontracts.edit.text" />"><b><bean:message
				key="main.employeecontract.edit.text" /></b></th>
			<th align="left"
				title="<bean:message key="main.headlinedescription.employeecontracts.delete.text" />"><b><bean:message
				key="main.employeecontract.delete.text" /></b></th>
		</c:if>
	</tr>

	<c:forEach var="employeecontract" items="${employeecontracts}"
		varStatus="statusID">
		<c:if
			test="${employeecontract.employee.lastname!='admin' || loginEmployee.status eq 'adm'}">
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
				<div class="tooltip" id="info<c:out value='${employeecontract.id}' />">
					<table>
						<tr>
							<td class="info">id:</td>
							<td class="info" colspan="3"><c:out
								value="${employeecontract.id}" /></td>
						</tr>
						<tr>
						<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.created" />:</td>
							<td class="info"><c:out value="${employeecontract.created}" /></td>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.by" /></td>
							<td class="info" valign="top"><c:out
								value="${employeecontract.createdby}" /></td>
						</tr>
						<tr>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.edited" />:</td>
							<td class="info"><c:out value="${employeecontract.lastupdate}" /></td>
							<td class="info" valign="top"><bean:message
								key="main.timereport.tooltip.by" /></td>
							<td class="info" valign="top"><c:out
								value="${employeecontract.lastupdatedby}" /></td>
						</tr>
						<tr>
							<td class="info" valign="top"><bean:message
								key="main.general.hide" />:</td>
							<td class="info"><c:choose><c:when test="${employeecontract.hide == true}"><bean:message
								key="main.general.yes" /></c:when><c:otherwise><bean:message
								key="main.general.no" /></c:otherwise></c:choose></td>
						</tr>
					</table>
				</div>
				<img onMouseOver="showWMTT(this,'info<c:out value="${employeecontract.id}" />')"
					onMouseOut="hideWMTT()" width="12px" height="12px"
					src="images/info_button.gif" />
			</td>
			
			<c:choose>
				<c:when test="${!employeecontract.currentlyValid}">
					<td style="color:gray"><c:out value="${employeecontract.employee.name}" /></td>
					<td style="color:gray"><c:out value="${employeecontract.taskDescription}" />&nbsp;</td>
<%--	änderung				--%>
					<td style="color:gray"><c:out value="${employeecontract.supervisor.name}" />&nbsp;</td>
					<td style="color:gray"><c:out value="${employeecontract.validFrom}" /></td>
					<td style="color:gray">
						<c:choose>
							<c:when test="${employeecontract.validUntil == null}">
								<bean:message key="main.general.open.text" />
							</c:when>
							<c:otherwise>
								<c:out value="${employeecontract.validUntil}" />
							</c:otherwise>
						</c:choose>
					</td>
					<td align="center"><html:checkbox name="employeecontract"
						property="freelancer" disabled="true" /></td>
					<td align="center" style="color:gray"><c:out
						value="${employeecontract.dailyWorkingTime}" /></td>
					<td align="center" style="color:gray"><c:out
						value="${employeecontract.vacationEntitlement}" /></td>
				</c:when>
				<c:otherwise>
					<td><c:out value="${employeecontract.employee.name}" /></td>
					<td><c:out value="${employeecontract.taskDescription}" />&nbsp;</td>
	<%--	änderung				--%>
					<td><c:out value="${employeecontract.supervisor.name}" />&nbsp;</td>
					<td><c:out value="${employeecontract.validFrom}" /></td>
					<td>
						<c:choose>
							<c:when test="${employeecontract.validUntil == null}">
								<bean:message key="main.general.open.text" />
							</c:when>
							<c:otherwise>
								<c:out value="${employeecontract.validUntil}" />
							</c:otherwise>
						</c:choose>
					</td>
					<td align="center"><html:checkbox name="employeecontract"
						property="freelancer" disabled="true" /></td>
					<td align="center"><c:out
						value="${employeecontract.dailyWorkingTime}" /></td>
					<td align="center"><c:out
						value="${employeecontract.vacationEntitlement}" /></td>
				</c:otherwise>
			</c:choose>
			
			<c:if test="${employeeAuthorized}">
				<td align="center"><html:link
					href="../do/EditEmployeecontract?ecId=${employeecontract.id}">
					<img src="images/Edit.gif" alt="Edit Employeecontract" title="<bean:message key="main.headlinedescription.employeecontracts.edit.text"/>"/>
				</html:link></td>
				<html:form action="/DeleteEmployeecontract">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employeecontract.id})"
						src="images/Delete.gif" alt="Delete Employeecontract" titleKey="main.headlinedescription.employeecontracts.delete.text"/></td>
				</html:form>
			</c:if>
			</tr>
		</c:if>
	</c:forEach>
	<c:if test="${employeeAuthorized}">
		<tr>
			<html:form action="/CreateEmployeecontract">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button"
					titleKey="main.general.button.createemployeecontract.alttext.text">
					<bean:message key="main.general.button.createemployeecontract.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
<br><br><br><br>
</body>
</html:html>
