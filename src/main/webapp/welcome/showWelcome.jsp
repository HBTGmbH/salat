<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<html>
<head>
<title><bean:message key="main.general.application.title" /> - <bean:message
	key="main.general.mainmenu.welcome.title.text" /></title>
<jsp:include flush="true" page="/head-includes.jsp" />
<script type="text/javascript" language="JavaScript">
	
	function setUpdate(form) {	
 		form.action = "/do/ShowWelcome?task=refresh";
		form.submit();
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
<c:if test="${currentEmployeeContract.employee.sign eq null}">
	<h1>Not logged in</h1>
</c:if>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<div style="width: 100%; text-align: center">
	<h1>Probier die neue <a href="/swagger-ui.html" style="color:black">REST API</a> aus!</h1>
</div>
<br><span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.overview.text" /><br></span>
<br>
<html:form action="/ShowWelcome">
&nbsp;<html:select property="employeeContractId" onchange="setUpdate(this.form)"
			 value="${currentEmployeeContract.id}" styleClass="make-select2">
	<c:forEach var="employeecontract" items="${employeecontracts}" >
			<html:option value="${employeecontract.id}">
				<c:out value="${employeecontract.employee.name}" /> |
				<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if 
				test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)
			</html:option>
	</c:forEach>
</html:select>
</html:form>
<jsp:include flush="true" page="/info2.jsp">
	<jsp:param name="info" value="Info" />
</jsp:include>
<br>
<!-- warnings -->
<c:if test="${warningsPresent}">
	<table border="0" cellspacing="0" cellpadding="2" width="100%"
			class="center backgroundcolor">
		<tr>
			<th align="left" colspan="2">
				<b><bean:message key="main.info.headline.warning" /></b>
			</th>
		</tr>
		<c:forEach var="warning" items="${warnings}" >
			<tr>
				<td class="noBborderStyle" align="left" width="5%" nowrap="nowrap">
					<span style="color:red"><c:out value="${warning.sort}" />:</span>		
				</td>
				<td class="noBborderStyle" align="left">
					<html:link style="color:red" href="${warning.link}"><c:out value="${warning.text}" /></html:link>
				</td>
			</tr>
		</c:forEach>
	</table>
</c:if>
</body>
</html>
