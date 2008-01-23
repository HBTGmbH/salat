<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

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
<c:if test="${currentEmployeeContract.employee.sign eq null}">
	<jsp:forward page="/login.jsp" />
</c:if>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br><span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.overview.text" /><br></span>
<br>
<html:form action="/ShowWelcome">
&nbsp;<html:select property="employeeContractId" onchange="setUpdate(this.form)"
			 value="${currentEmployeeContract.id}">
	<c:forEach var="employeecontract" items="${employeecontracts}" >
		<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
			<html:option value="${employeecontract.id}">
				<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if 
				test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)
			</html:option>
		</c:if>							
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
