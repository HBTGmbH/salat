<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<html:html>
	<head>
		<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.employees.text" /></title>
		<jsp:include flush="true" page="/head-includes.jsp" />
		<script type="text/javascript" language="JavaScript">
			function refresh(form) {
				form.action = "/do/ShowOvertime?task=refresh";
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
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.overtime.text" /><br></span>
	<br>
	<span style="color:red"><html:errors footer="<br>" /> </span>

	<table class="center backgroundcolor">
		<html:form action="/ShowOvertime?task=refresh">
			<tr>
				<td class="noBborderStyle" colspan="2">
					<b><bean:message key="main.overtime.employeecontract.text" /></b>
				</td>
				<td class="noBborderStyle" colspan="9" align="left">
					<html:select property="employeecontractId" onchange="refresh(this.form)" styleClass="make-select2">
						<c:if test="${authorizedUser.manager}">
							<html:option value="-1">
								<bean:message key="main.general.allemployees.text" />
							</html:option>
						</c:if>
						<c:forEach var="employeecontract" items="${employeecontracts}">
							<html:option value="${employeecontract.id}">
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
		</html:form>
	</table>
	<br><br><br>
	</body>
</html:html>
