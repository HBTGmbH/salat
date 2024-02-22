<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<html:html>
<head>

<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.employees.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
<script type="text/javascript" language="JavaScript">
 
	function confirmDelete(form, id) {	
		var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
		if (agree) {
			form.action = "/do/DeleteEmployee?emId=" + id;
			form.submit();
		}
	}	
	
	function refresh(form) {	
		form.action = "/do/ShowEmployee?task=refresh";
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
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.employees.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>" /> </span>

<table class="center backgroundcolor">
<html:form action="/ShowEmployee?task=refresh">
	<tr>
		<td class="noBborderStyle" colspan="2"><b><bean:message key="main.general.filter.text" /></b></td>
		<td class="noBborderStyle" colspan="9" align="left">
			<html:text property="filter" size="40" />
			<html:submit styleId="button" titleKey="main.general.button.filter.alttext.text">
				<bean:message key="main.general.button.filter.text" />
			</html:submit>
		</td>
	</tr>
</html:form>
<bean:size id="employeesSize" name="employees" />
<c:if test="${employeesSize>10}">
	<c:if test="${authorizedUser.manager}">
		<tr>
			<html:form action="/CreateEmployee">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createemployee.alttext.text">
					<bean:message key="main.general.button.createemployee.text" />
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
			key="main.headlinedescription.employees.lastname.text" />"><b><bean:message
				key="main.employee.lastname.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.fristname.text" />"><b><bean:message
			key="main.employee.firstname.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.sign.text" />"><b><bean:message
			key="main.employee.sign.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.loginname.text" />"><b><bean:message
			key="main.employee.loginname.text" /></b></th>
		<!--  
		<td align="left"> <b><bean:message key="main.employee.password.text"/></b> </th>
		-->
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.status.text" />"><b><bean:message
			key="main.employee.status.text" /></b></th>
		<th align="left"
			title="<bean:message
			key="main.headlinedescription.employees.gender.text" />"><b><bean:message
			key="main.employee.gender.text" /></b></th>
		<c:if test="${authorizedUser.manager}">
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.employees.edit.text" />"><b><bean:message
				key="main.employee.edit.text" /></b></th>
			<th align="left"
				title="<bean:message
				key="main.headlinedescription.employees.delete.text" />"><b><bean:message
				key="main.employee.delete.text" /></b></th>
		</c:if>
	</tr>

	<c:forEach var="employee" items="${employees}" varStatus="statusID">
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
		<div class="tooltip" id="info<c:out value='${employee.id}' />">
		<table>
			<tr>
				<td class="info">id:</td>
				<td class="info" colspan="3"><c:out
					value="${employee.id}" /></td>
			</tr>
			<tr>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.created" />:</td>
				<td class="info"><c:out value="${employee.created}" /></td>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.by" /></td>
				<td class="info" valign="top"><c:out
					value="${employee.createdby}" /></td>
			</tr>
			<tr>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.edited" />:</td>
				<td class="info"><c:out value="${employee.lastupdate}" /></td>
				<td class="info" valign="top"><bean:message
					key="main.timereport.tooltip.by" /></td>
				<td class="info" valign="top"><c:out
					value="${employee.lastupdatedby}" /></td>
			</tr>
		</table>

		</div>
		<img
			onMouseOver="showWMTT(this,'info<c:out value="${employee.id}" />')"
			onMouseOut="hideWMTT()" width="12px" height="12px"
			src="/images/info_button.gif" />
		</td>

		<td><c:out value="${employee.lastname}" /></td>
		<td><c:out value="${employee.firstname}" /></td>
		<td><c:out value="${employee.sign}" /></td>
		<td><c:out value="${employee.loginname}" /></td>
		<!--
	<td><bean:write name="employee" property="password"/></td>
	-->
		<td><c:out value="${employee.status}" /></td>
		<td align="center"><c:out value="${employee.gender}" /></td>
		<c:if test="${authorizedUser.manager}">
			<td align="center"><html:link
				href="/do/EditEmployee?emId=${employee.id}">
				<img src="/images/Edit.gif" alt="Edit Employee" title="<bean:message key="main.headlinedescription.employees.edit.text"/>"/>
			</html:link></td>
			<html:form action="/DeleteEmployee">
				<td align="center"><html:image
					onclick="confirmDelete(this.form, ${employee.id})"
					src="/images/Delete.gif" alt="Delete Employee" titleKey="main.headlinedescription.employees.delete.text"/></td>
			</html:form>
		</c:if>
		</tr>
	</c:forEach>
	<c:if test="${authorizedUser.manager}">
		<tr>
			<html:form action="/CreateEmployee">
				<td class="noBborderStyle" colspan="4"><html:submit
					styleId="button" titleKey="main.general.button.createemployee.alttext.text">
					<bean:message key="main.general.button.createemployee.text" />
				</html:submit></td>
			</html:form>
		</tr>
	</c:if>

</table>
<br><br><br>
</body>
</html:html>
