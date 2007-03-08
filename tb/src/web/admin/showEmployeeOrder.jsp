<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%

%>

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.mainmenu.employeeorders.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/tb/do/DeleteEmployeeorder?eoId=" + id;
			form.submit();
		}
	}
	
	function setUpdateEmployeeOrders(form) {
		form.action = "/tb/do/ShowEmployeeorder";
		form.submit();
	
	}				
 
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.employeeorders.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<html:form action="/ShowEmployeeorder">
	<table class="center backgroundcolor">
		<!-- select employeecontract -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.monthlyreport.employee.fullname.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<html:select
					property="employeeContractId"		
					onchange="setUpdateEmployeeOrders(this.form)" >
					<html:option value="-1">
						<bean:message key="main.general.allemployees.text" />
					</html:option>
					<c:forEach var="employeecontract" items="${employeecontracts}" >
						<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
							<html:option value="${employeecontract.id}">
								<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if 
									test="${employeecontract.openEnd}"><bean:message 
									key="main.general.open.text" /></c:if>)
							</html:option>
						</c:if>							
					</c:forEach>
				</html:select> 		
			</td>
		</tr>
		<!-- select order -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.customerorder.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="orderId" onchange="setUpdateEmployeeOrders(this.form)"
				value="${currentOrderId}">
				<html:option value="-1">
					<bean:message key="main.general.allorders.text" />
				</html:option>
				<html:options collection="orders" labelProperty="signAndDescription"
					property="id" />
			</html:select></td>
		</tr>
	</table>
</html:form>

<table class="center backgroundcolor">
	<bean:size id="employeeordersSize" name="employeeorders" />
	<c:if test="${employeeordersSize>10}">
		<c:if test="${employeeAuthorized || employeeIsResponsible}">
			<tr>
				<html:form action="/CreateEmployeeorder">
					<td class="noBborderStyle" colspan="4"><html:submit
						styleId="button" titleKey="main.general.button.createemployeeorder.alttext.text">
						<bean:message key="main.general.button.createemployeeorder.text" />
					</html:submit></td>
				</html:form>
			</tr>
		</c:if>
	</c:if>
	<tr>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.employeename.text" />"><b><bean:message
			key="main.employeeorder.employee.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.ordernumber.text" />"><b><bean:message
			key="main.employeeorder.customerorder.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.subordernumber.text" />"><b><bean:message
			key="main.employeeorder.suborder.text" /></b></th>
		<!--  
		<td align="left"> <b><bean:message key="main.employeeorder.sign.text"/></b> </td>	
		-->
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.validfrom.text" />"><b><bean:message
			key="main.employeeorder.validfrom.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.validuntil.text" />"><b><bean:message
			key="main.employeeorder.validuntil.text" /></b></th>
		<th align="center"
			title="<bean:message
			key="main.headlinedescription.employeeorders.standingorder.text" />"><b><bean:message
			key="main.employeeorder.standingorder.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.debit.text" />"><b><bean:message
			key="main.employeeorder.debithours.text" /></b></th>
		<!--  
		<th align="left" title="<bean:message
			key="main.headlinedescription.employeeorders.status.text" />"><b><bean:message
			key="main.employeeorder.status.text" /></b></th>
		-->
		<th align="center"
			title="<bean:message
			key="main.headlinedescription.employeeorders.statusreport.text" />"><b><bean:message
			key="main.employeeorder.statusreport.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.edit.text" />"><b><bean:message
			key="main.employeeorder.edit.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employeeorders.delete.text" />"><b><bean:message
			key="main.employeeorder.delete.text" /></b></th>
	</tr>
	<c:forEach var="employeeorder" items="${employeeorders}"
		varStatus="statusID">
		<c:choose>
			<c:when test="${statusID.count%2==0}">
				<tr class="primarycolor">
			</c:when>
			<c:otherwise>
				<tr class="secondarycolor">
			</c:otherwise>
		</c:choose>
		<td title="<c:out value="${employeeorder.employeecontract.employee.name}" />&nbsp;&nbsp;(<c:out value="${employeeorder.employeecontract.timeString}" /><c:if 
				test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)"><c:out value="${employeeorder.employeecontract.employee.sign}" /></td>
		<td
			title="<c:out value="${employeeorder.suborder.customerorder.description}" />"><c:out
			value="${employeeorder.suborder.customerorder.sign}" /></td>
		<td title="<c:out value="${employeeorder.suborder.description}" />"><c:out
			value="${employeeorder.suborder.sign}" /></td>
		<!-- 
      	<td><bean:write name="employeeorder" property="sign"/></td>
      	 -->
		<td><c:out value="${employeeorder.fromDate}" /></td>
		<td>
			<c:choose>
				<c:when test="${employeeorder.untilDate == null}">
					<bean:message key="main.employeeorder.openend.text" />
				</c:when>
				<c:otherwise>
					<c:out value="${employeeorder.untilDate}" />
				</c:otherwise>
			</c:choose>
		</td>
		<td align="center"><html:checkbox name="employeeorder"
			property="standingorder" disabled="true" /></td>
		<td><c:out value="${employeeorder.debithours}" /></td>
		<!--  
		<c:choose>
			<c:when test="${employeeorder.status==''}">
				<td>&nbsp;</td>
			</c:when>
			<c:otherwise>
				<td><c:out value="${employeeorder.status}" /></td>
			</c:otherwise>
		</c:choose>
		-->
		<td align="center"><html:checkbox name="employeeorder"
			property="statusreport" disabled="true" /></td>

		<c:choose>
			<c:when
				test="${employeeAuthorized || employeeorder.suborder.customerorder.responsible_hbt.id == loginEmployee.id}">
				<td align="center"><html:link
					href="/tb/do/EditEmployeeorder?eoId=${employeeorder.id}">
					<img src="/tb/images/Edit.gif" alt="Edit Employeeorder"  titleKey="main.headlinedescription.employeeorders.edit.text"/>
				</html:link></td>
				<html:form action="/DeleteEmployeeorder">
					<td align="center"><html:image
						onclick="confirmDelete(this.form, ${employeeorder.id})"
						src="/tb/images/Delete.gif" alt="Delete Employeeorder"  titleKey="main.headlinedescription.employeeorders.delete.text"/></td>
				</html:form>
			</c:when>
			<c:otherwise>
				<td align="center"><img height="12px" width="12px"
					src="/tb/images/verbot.gif" alt="Edit Employeeorder" title="<bean:message key="main.headlinedescription.employeeorders.accessdenied.text"/>"/></td>
				<td align="center"><img height="12px" width="12px"
					src="/tb/images/verbot.gif" alt="Delete Employeeorder" title="<bean:message key="main.headlinedescription.employeeorders.accessdenied.text"/>"/></td>
			</c:otherwise>
		</c:choose>
		</tr>
	</c:forEach>
	<c:if test="${employeeAuthorized || employeeIsResponsible}">
		<tr>
			<html:form action="/CreateEmployeeorder">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createemployeeorder.alttext.text">
					<bean:message key="main.general.button.createemployeeorder.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>
</table>
</body>
</html:html>
