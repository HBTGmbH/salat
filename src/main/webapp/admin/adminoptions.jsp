<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - Admin</title>
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />
<script type="text/javascript" language="JavaScript">
	
	function setAction(form, actionVal) {	
 		form.action = "/tb/do/ShowAdminOptions?task=" + actionVal;
		form.submit();
	}
	
</script>
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br><span style="font-size:14pt;font-weight:bold;"><br><bean:message key="adminarea.title" /><br></span>
<br>
<html:form action="/ShowAdminOptions">
	
	<html:submit
		onclick="setAction(this.form, 'SetEmployeeOrderInTimereports');return false" styleId="button">
		<bean:message key="adminarea.seteoitr.button.text" />
	</html:submit>
	<bean:message key="adminarea.seteoitr.description" />
	<br><br>
	<c:out value="${setemployeeorderresults}" />
	<br><br>
	<c:if test="${unassignedreports != null}">
		<c:forEach var="timereport" items="${unassignedreports}" >
			[id:<c:out value="${timereport.id}" />&nbsp;&nbsp;|&nbsp;&nbsp;
			ec:<c:out value="${timereport.employeecontract.id}" />&nbsp;&nbsp;|&nbsp;&nbsp;
			so:<c:out value="${timereport.suborder.id}" />&nbsp;&nbsp;|&nbsp;&nbsp;
			emp:<c:out value="${timereport.employeecontract.employee.sign}" />&nbsp;&nbsp;|&nbsp;&nbsp;
			order:<c:out value="${timereport.suborder.customerorder.sign}" />&nbsp;/&nbsp;
			<c:out value="${timereport.suborder.sign}" />&nbsp;&nbsp;|&nbsp;&nbsp;
			date:<c:out value="${timereport.referenceday.refdate}" />]<br>
		</c:forEach>
	</c:if>
	
	<c:if test="${problems != null}">
		<br><br>Problems:<br>
		<c:forEach var="problem" items="${problems}" >
			<c:out value="${problem}" /><br>
		</c:forEach>
	</c:if>
</html:form>
</body>
</html>