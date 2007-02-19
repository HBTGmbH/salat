<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message
	key="main.general.mainmenu.welcome.title.text" /></title>
<script type="text/javascript" language="JavaScript">
	
	function setUpdate(form) {	
 		form.action = "/tb/do/ShowWelcome?task=refresh";
		form.submit();
	}
	
</script>
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<center><br>
<br>
<br>
<h3 style="color: black"><bean:message key="main.general.mainmenu.hello.text" />&nbsp;<c:out value="${loginEmployee.name}" />
<br>
<br>
<bean:message key="main.general.mainmenu.welcome.text" /></h3>
<br>
<br>
<html:form action="/ShowWelcome">
<html:select property="employeeContractId" onchange="setUpdate(this.form)"
			 value="${currentEmployeeContract.id}">
	<c:forEach var="employeecontract" items="${employeecontracts}" >
		<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
			<html:option value="${employeecontract.id}">
				<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" />)
			</html:option>
		</c:if>							
	</c:forEach>
</html:select>
</html:form>
<jsp:include flush="true" page="/info.jsp">
	<jsp:param name="info" value="Info" />
</jsp:include>
</center>
</body>
</html>
