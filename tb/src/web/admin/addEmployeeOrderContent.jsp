<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>


<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.addemployeeordercontent.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal) {	
 		form.action = "/tb/do/StoreEmployeeOrderContent?action=" + actionVal;
		form.submit();
	}
				
</script>
</head>
<body>

<html:form action="/StoreEmployeeOrderContent">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br>
	<bean:message key="main.general.enteremployeeordercontentproperties.text" />:<br>
	</span>
	<br>
	<br>
	<div style="font-size: 12pt;"><i><c:out value="${actionInfo}" />&nbsp;</i></div>
	<br>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor" width="100%">
		
		<!-- Buttons -->
		<tr><td colspan="3" class="noBborderStyle">
			<html:submit onclick="setStoreAction(this.form, 'back')"
				styleId="button"><bean:message key="employeeordercontent.button.back.text"/></html:submit>
			<c:if test="${contentIsEditable}">
				<html:submit onclick="setStoreAction(this.form, 'save')"
					styleId="button"><bean:message key="employeeordercontent.button.save.text"/></html:submit></c:if>
			<c:if test="${releaseEmpPossible}">
				<html:submit onclick="setStoreAction(this.form, 'releaseEmp')"
					styleId="button"><bean:message key="employeeordercontent.button.release.employee.text"/></html:submit></c:if>
			<c:if test="${releaseMgmtPossible}">
				<html:submit onclick="setStoreAction(this.form, 'releaseMgmt')"
					styleId="button"><bean:message key="employeeordercontent.button.release.management.text"/></html:submit></c:if>
			<c:if test="${loginEmployee.status == 'adm' && currentEmployeeOrder.employeeordercontent != null}">
				<html:submit onclick="setStoreAction(this.form, 'removeRelease')"
					styleId="button"><bean:message key="employeeordercontent.button.removerelease.text"/></html:submit>
				<html:submit onclick="setStoreAction(this.form, 'deleteContent')"
					styleId="button"><bean:message key="employeeordercontent.button.delete.text"/></html:submit></c:if>
			<br>&nbsp;
		</td></tr>
		
		
		<tr>
			<th align="left" colspan="3">
				<c:out value="${contentStatus}" />&nbsp;
			</th>
		</tr>
		
		<!-- Employee -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.employee.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<c:out value="${currentEmployeeOrder.employeecontract.employee.name}" />
			</td>
		</tr>
		
		<!-- Order -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.order.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<c:out value="${currentEmployeeOrder.suborder.customerorder.sign}" /> <c:out value="${currentEmployeeOrder.suborder.customerorder.description}" /><br>
				<c:out value="${currentEmployeeOrder.suborder.sign}" /> <c:out value="${currentEmployeeOrder.suborder.description}" />
			</td>
		</tr>
		
		<!-- Time period -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.timeperiod.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<c:out value="${currentEmployeeOrder.fromDate}" /> bis <c:out value="${currentEmployeeOrder.untilDate}" /><c:if 
					test="${currentEmployeeOrder.untilDate == null}"><bean:message key="main.general.open.text" /></c:if>
			</td>
		</tr>
		
		<!-- Debithours -->
		<tr>
			<td class="noBborderStyle">
				<b><bean:message key="employeeordercontent.debithours.text"/>:</b>
			</td>
			<td class="noBborderStyle" colspan="2">
				<c:choose>
					<c:when test="${currentEmployeeOrder.debithours == null || currentEmployeeOrder.debithours == 0.0}">
						./.
					</c:when>
					<c:otherwise>
						<c:out value="${currentEmployeeOrder.debithours}" />
						<c:choose>
							<c:when test="${currentEmployeeOrder.debithoursunit == 0}">
								/ <bean:message key="main.general.totaltime.text" />
							</c:when>
							<c:when test="${currentEmployeeOrder.debithoursunit == 1}">
								/ <bean:message key="main.general.year.text" />
							</c:when>
							<c:when test="${currentEmployeeOrder.debithoursunit == 12}">
								/ <bean:message key="main.general.month.text" />
							</c:when>
							<c:otherwise>
								?
							</c:otherwise>
						</c:choose>
					</c:otherwise>
				</c:choose>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<!-- WIKI -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.furtherapplicablematters.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<c:choose>
					<c:when test="${clientIntern}">
						<html:link style="color: #7390b1" target="_blank" href="http://wiki/mediawiki/index.php/Prozesshandbuch/Themen/Mitarbeiterauftrag">
							<b><bean:message key="employeeordercontent.staticmatters.text"/></b>
						</html:link>
					</c:when>
					<c:otherwise>
						<html:link style="color: #7390b1" target="_blank" href="https://wiki.hbt.de/mediawiki/index.php/Prozesshandbuch/Themen/Mitarbeiterauftrag">
							<b><bean:message key="employeeordercontent.staticmatters.text"/></b>
						</html:link>
					</c:otherwise>
				</c:choose>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
	
	
		<!-- Description -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.description.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:textarea property="description" cols="100" rows="5" readonly="${!contentIsEditable}" />
				<span style="color:red"><br><html:errors property="description" /></span>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		
		<!-- Task -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.task.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:textarea property="task" cols="100" rows="5" readonly="${!contentIsEditable}" />
				<span style="color:red"><br><html:errors property="task" /></span>
			</td>
		</tr>
		
		<!-- Boundary -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.boundary.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:textarea property="boundary" cols="100" rows="5" readonly="${!contentIsEditable}" />
				<span style="color:red"><br><html:errors property="boundary" /></span>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<!-- Procedure -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.procedure.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:textarea property="procedure" cols="100" rows="5" readonly="${!contentIsEditable}" />
				<span style="color:red"><br><html:errors property="procedure" /></span>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.process.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:select property="qm_process_id" disabled="${!contentIsEditable}">
					<c:forEach var="process" items="${qm_processes}">
						<html:option value="${process.intValue}"><bean:message key="${process.label}" /></html:option>
					</c:forEach>
				</html:select>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<!-- Contact -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.contact.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top">
				<bean:message key="employeeordercontent.contact.hbt.text"/>	
			</td>
			<td class="noBborderStyle" valign="top">
				<bean:message key="employeeordercontent.contact.customer.text"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" valign="top">
				<bean:message key="employeeordercontent.contact.contractually.text"/>	
			</td>
			<td class="noBborderStyle" valign="top">
				<html:select property="contact_contract_hbt_emp_id" disabled="${!contentIsEditable}">
					<html:options collection="allEmployees" labelProperty="name" property="id"/>
				</html:select>
			</td>
			<td class="noBborderStyle" valign="top">
				<html:text property="contact_contract_customer" maxlength="64" size="64" disabled="${!contentIsEditable}"/>
				<span style="color:red"><html:errors property="contact_contract_customer" /></span>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" valign="top">
				<bean:message key="employeeordercontent.contact.technical.text"/>	
			</td>
			<td class="noBborderStyle" valign="top">
				<html:select property="contact_tech_hbt_emp_id" disabled="${!contentIsEditable}">
					<html:options collection="allEmployees" labelProperty="name" property="id"/>
				</html:select>
			</td>
			<td class="noBborderStyle" valign="top">
				<html:text property="contact_tech_customer" maxlength="64" size="64" disabled="${!contentIsEditable}"/>
				<span style="color:red"><html:errors property="contact_tech_customer" /></span>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<!-- Additional risks -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.additionalrisks.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:textarea property="additional_risks" cols="100" rows="5" readonly="${!contentIsEditable}" />
				<span style="color:red"><br><html:errors property="risks" /></span>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<!-- Arrangement -->
		<tr>
			<td class="noBborderStyle" valign="top">
				<b><bean:message key="employeeordercontent.arrangement.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top" colspan="2">
				<html:textarea property="arrangement" cols="100" rows="5" readonly="${!contentIsEditable}" />
				<span style="color:red"><br><html:errors property="arrangement" /></span>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<tr>
			<td class="noBborderStyle" valign="top" rowspan="2">
				<b><bean:message key="employeeordercontent.releasestatus.text"/>:</b>
			</td>
			<td class="noBborderStyle" valign="top">
				<bean:message key="employeeordercontent.employee.text"/>
			</td>
			<td class="noBborderStyle" valign="top">
				<bean:message key="employeeordercontent.management.text"/>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle" valign="top">
				<c:choose>
					<c:when test="${currentEmployeeOrder.employeeordercontent == null || !currentEmployeeOrder.employeeordercontent.committed_emp}">
						<i><bean:message key="employeeordercontent.releasestatus.notreleased.text"/></i>
					</c:when>
					<c:otherwise>
						<i><bean:message key="employeeordercontent.releasestatus.released.text"/> (<c:out value="${currentEmployeeOrder.employeeordercontent.committedby_emp.name}" />)</i>
					</c:otherwise>
				</c:choose>
			</td>
			<td class="noBborderStyle" valign="top">
				<c:choose>
					<c:when test="${currentEmployeeOrder.employeeordercontent == null || !currentEmployeeOrder.employeeordercontent.committed_mgmt}">
						<i><bean:message key="employeeordercontent.releasestatus.notreleased.text"/></i>
					</c:when>
					<c:otherwise>
						<i><bean:message key="employeeordercontent.releasestatus.released.text"/> (<c:out value="${currentEmployeeOrder.employeeordercontent.committedby_mgmt.name}" />)</i>
					</c:otherwise>
				</c:choose>
			</td>
		</tr>
		
		<tr><td colspan="3" class="noBborderStyle"><hr></td></tr>
		
		<!-- Buttons -->
		<tr><td colspan="3" class="noBborderStyle">
			<html:submit onclick="setStoreAction(this.form, 'back')"
				styleId="button"><bean:message key="employeeordercontent.button.back.text"/></html:submit>
			<c:if test="${contentIsEditable}">
				<html:submit onclick="setStoreAction(this.form, 'save')"
					styleId="button"><bean:message key="employeeordercontent.button.save.text"/></html:submit></c:if>
			<c:if test="${releaseEmpPossible}">
				<html:submit onclick="setStoreAction(this.form, 'releaseEmp')"
					styleId="button"><bean:message key="employeeordercontent.button.release.employee.text"/></html:submit></c:if>
			<c:if test="${releaseMgmtPossible}">
				<html:submit onclick="setStoreAction(this.form, 'releaseMgmt')"
					styleId="button"><bean:message key="employeeordercontent.button.release.management.text"/></html:submit></c:if>
			<c:if test="${loginEmployee.status == 'adm' && currentEmployeeOrder.employeeordercontent != null}">
				<html:submit onclick="setStoreAction(this.form, 'removeRelease')"
					styleId="button"><bean:message key="employeeordercontent.button.removerelease.text"/></html:submit>
				<html:submit onclick="setStoreAction(this.form, 'deleteContent')"
					styleId="button"><bean:message key="employeeordercontent.button.delete.text"/></html:submit></c:if>
		</td></tr>		
	</table>
		
	
	
	
	
</html:form>
</body>
</html>
