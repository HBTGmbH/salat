<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="/WEB-INF/treeTag.tld" prefix="myjsp"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>
<head>
<html:base />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.multipleEmployeeorders.text" /></title>
<link rel="stylesheet" type="text/css" href="../style/tb.css" />
<link href="../style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="../favicon.ico" />
<script src=""../scripts/"jquery-1.11.3.min.js"></script>
<script src=""../scripts/"select2.full.min.js"></script>
<script type="text/javascript" language="JavaScript">
function refresh(form) {	
	form.action = "../do/GenerateMultipleEmployeeorders?task=refresh";
	form.submit();
}

function multipleChange(form) {
	    	form.action = "../do/GenerateMultipleEmployeeorders?task=multiplechange";
			form.submit();
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
	<bean:message key="main.general.mainmenu.multipleEmployeeorders.text" /><br>
	</span>
	<br>
	<span style="color: red"><html:errors footer="<br>" /> </span>


	<html:form action="/GenerateMultipleEmployeeorders?task=refresh">
	<table class="center backgroundcolor">	
		<!-- Select Order -->
		<tr>
			<td class="noBborderStyle" align="left" ><b><bean:message
				key="main.suborder.customerorder.text" /></b></td>
			<td class="noBborderStyle"  align="left">
				<html:select property="customerOrderId" onchange="refresh(this.form)" value="${currentCustomer}" styleClass="make-select2">
					<html:option value="-1">
						<bean:message key="main.general.allorders.text" />
					</html:option>
					<html:options collection="visibleCustomerOrders" labelProperty="signAndDescription" property="id" />
				</html:select>	
			</td>
		</tr>

		<!-- Select Suborder -->
		<tr>
	        <td class="noBborderStyle" align="left" >
	        	<b><bean:message key="main.employeeorder.suborder.text" />:</b>
	        </td>
			<td class="noBborderStyle" align="left" >
	           <html:select property="suborderId" onchange="refresh(this.form)" value="${currentSuborder}" styleClass="make-select2">
	           		<c:choose>
	         		<c:when test="${showAllSuborders == true}">
	           			<html:option value="-1">
							<bean:message key="main.general.allsuborders.text" />
						</html:option>
					</c:when>
					</c:choose>
					<c:forEach var="suborder" items="${suborders}">
						<html:option value="${suborder.id}">
							<c:out value="${suborder.signAndDescription}"/>
							<c:if test="${!suborder.currentlyValid}">
								&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
							</c:if>
						</html:option>
					</c:forEach>
				</html:select>	
				<span style="font-size: 0.6em">
					<bean:message key="main.general.select.expired.text" />
				</span>
				<html:checkbox property="showOnlyValid" onclick="refresh(this.form)" styleClass="middle-aligned">
					<span class="middle-aligned"><bean:message key="main.general.show.only.valid.text"/></span>
				</html:checkbox>
			</td>		
		</tr>
		<tr>
			<td align="left" class="noBborderStyle" colspan="2"> <b><bean:message key ="main.multipleEmployeeorders.note.text"/></b></td>
		</tr>
	</table>
	</html:form>

	<html:form action="/GenerateMultipleEmployeeorders">
	
	<bean:size id="employeecontractsSize" name="employeecontracts" />
	<table>
		<c:if test="${employeecontractsSize>10}">
				<tr>
					<td class="noBborderStyle">
					<html:submit onclick="multipleChange(this.form)" styleId="button" titleKey="main.general.button.generateEmployeeorders.alttext.text">
						<bean:message key="main.general.button.generateEmployeeorders.text" />
					</html:submit>
					</td>
				</tr>
		</c:if>
		<tr>
			<th align="left" title="select">&nbsp;</th>
			<th align="left"
				title="<bean:message key="main.headlinedescription.employeecontracts.employeename.text" />"><b><bean:message
				key="main.employeecontract.employee.text" /></b></th>
			<th align="left"
				title="<bean:message key="main.headlinedescription.employeecontracts.validfrom.text" />"><b><bean:message
				key="main.multipleemployeeorders.contractvalidfrom.text" /></b></th>
			<th align="left"
				title="<bean:message key="main.headlinedescription.employeecontracts.validuntil.text" />"><b><bean:message
				key="main.multipleemployeeorders.contractvaliduntil.text" /></b></th>
		</tr>	
		
		<c:forEach var="employeecontract" items="${employeecontracts}" varStatus="statusID">
		<tr class="${statusID.count%2==0 ? 'primarycolor' : 'secondarycolor'}">
					
			<!-- Checkbox -->
			<td align="center"><html:multibox property="employeecontractIdArray"  value="${employeecontract.id}" /></td>
	
			<td><c:out value="${employeecontract.employee.name}" /></td>
			<td><c:out value="${employeecontract.validFrom}" /></td>
			<td><c:choose>
				<c:when test="${employeecontract.validUntil == null}">
					<bean:message key="main.general.open.text" />
				</c:when>
				<c:otherwise>
					<c:out value="${employeecontract.validUntil}" />
				</c:otherwise>
			</c:choose>
			</td>
		</tr>
	
		</c:forEach>		

		<tr>
			<td class="noBborderStyle">
				<html:submit onclick="multipleChange(this.form)" styleId="button" titleKey="main.general.button.generateEmployeeorders.alttext.text">
					<bean:message key="main.general.button.generateEmployeeorders.text" />
				</html:submit>
			</td>
		</tr>
	</table>		
	</html:form>
</body>
</html:html>
