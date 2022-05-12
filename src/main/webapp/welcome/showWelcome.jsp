<%@ page import="org.tb.auth.AuthorizedUser" %>
<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%
	AuthorizedUser authorizedUser = (AuthorizedUser) request.getAttribute("authorizedUser");
	if(authorizedUser == null || !authorizedUser.isAuthenticated()) {
		response.sendRedirect("/auth/login.jsp");
		return;
	}
%>
<html>
<head>
	<jsp:include page="/common-head.jsp">
		<jsp:param name="title_message_key" value="main.general.mainmenu.welcome.title.text"/>
	</jsp:include>
</head>
<body>
<jsp:include page="/common-body-begin.jsp" />
<div class="container">
	<div style="width: 100%; text-align: center">
		<h1>Probier die neue <a href="/swagger-ui.html" style="color:black">REST API</a> aus!</h1>
	</div>
	<h1><bean:message key="main.general.mainmenu.overview.text" /></h1>
	<div class="row">
		<div class="col-12">
			<html:form action="/ShowWelcome">
				&nbsp;<html:select property="employeeContractId" onchange="setUpdate(this.form)"
								   value="${currentEmployeeContract.id}" styleClass="make-select2">
				<c:forEach var="employeecontract" items="${employeecontracts}" >
					<html:option value="${employeecontract.id}">
						<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if
							test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)
					</html:option>
				</c:forEach>
			</html:select>
			</html:form>
		</div>
	</div>
	<div class="row gx-3">
		<div class="col-md-4">
			<div class="card bg-light">
				<div class="card-body">
					<h5 class="card-title"><bean:message key="main.welcome.reports.text" /></h5>
					<h6 class="card-subtitle mb-2 text-muted">Card subtitle</h6>
					<p class="card-text">Some quick example text to build on the card title and make up the bulk of the card's content.</p>
					<a href="#" class="card-link">Card link</a>
					<a href="#" class="card-link">Another link</a>
				</div>
			</div>
		</div>
		<div class="col-md-4">
			<div class="card bg-light">
				<div class="card-body">
					<h5 class="card-title"><bean:message key="main.welcome.overtime.text" /></h5>
					<h6 class="card-subtitle mb-2 text-muted">Card subtitle</h6>
					<p class="card-text">Some quick example text to build on the card title and make up the bulk of the card's content.</p>
					<a href="#" class="card-link">Card link</a>
					<a href="#" class="card-link">Another link</a>
				</div>
			</div>
		</div>
		<div class="col-md-4">
			<div class="card">
				<div class="card-body">
					<h5 class="card-title"><bean:message key="main.welcome.vacation.text" /></h5>
					<h6 class="card-subtitle mb-2 text-muted">Card subtitle</h6>
					<p class="card-text">Some quick example text to build on the card title and make up the bulk of the card's content.</p>
					<a href="#" class="card-link">Card link</a>
					<a href="#" class="card-link">Another link</a>
				</div>
			</div>
		</div>
	</div>
</div>
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
<jsp:include page="/common-body-end.jsp" />
<script>
	function setUpdate(form) {
		form.action = "/do/ShowWelcome?task=refresh";
		form.submit();
	}
</script>
</body>
</html>
