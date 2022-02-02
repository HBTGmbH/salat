<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="/WEB-INF/treeTag.tld" prefix="myjsp" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%

%>

<html:html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.budget.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/style/tb.css" />
<link rel="shortcut icon" type="image/x-icon" href="/tb/favicon.ico" />
<script type="text/javascript" language="JavaScript">

function refresh(form, id) {	
		form.action = "/tb/do/ShowBudget?task=refresh&id=" + id;
		form.submit();
	}
</script>
</head>
<body>

<html:form action="/ShowBudget">
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.budget.text" /><br></span>
<br>
<span style="color:red"><html:errors footer="<br>"/>
</span>

<table class="center backgroundcolor">
		
		<!-- select customerorder -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.customerorder.text" /></b></td>
			<td align="left" class="noBborderStyle">
			<html:select 
				property="customerOrderId" 
				onchange="refresh(this.form, -1)">
				<html:option value="${currentOrderId}">
					<bean:message key="main.general.allorders.text" />
				</html:option>
				<html:options collection="visibleCustomerOrders"
					labelProperty="signAndDescription" property="id" />
			</html:select><span style="color:red"><html:errors property="customerorder" /></span></td>
		</tr>
</table>	
	<!-- show the order-structure -->
	<HR>
		<% String browser = request.getHeader("User-Agent");  %>
		 <myjsp:tree 
			mainProject="${currentOrder}" 
			subProjects="${suborders}"
			browser="<%=browser%>"  
			changeFunctionString="refresh(this.form, default)"
			defaultString="default"
			currentSuborderID="${soId}"
			/>	 
	<HR>
</html:form>
	
	<!-- Label for the choosen order -->

	<b><bean:message key="main.general.Budget.choosenOrder.text" />  "  ${orderOrSuborderSignAndDescription}"</b>
	<br>	<br>
	<!-- The buttons to do the budget calculations -->
	
	<c:if test="${employeeAuthorized}">
			<table class="center backgroundcolor">
				<tr>
					<td>
						<html:form action="/ShowBudget?task=calcStructure">
							<td class="noBborderStyle" colspan="4"><html:submit styleId="button" titleKey="main.general.button.Budget.calcStructure.alttext.text">
								<bean:message key="main.general.button.Budget.calcStructure.text" />
							</html:submit></td>
						</html:form>
					</td>
					<td>
						<html:form action="/ShowBudget?task=calcDebit">
							<td class="noBborderStyle" colspan="4"><html:submit styleId="button" titleKey="main.general.button.Budget.calcDebitHours.alttext.text">
								<bean:message key="main.general.button.Budget.calcDebitHours.text" />
							</html:submit></td>
						</html:form>
					</td>
					<td>
						<html:form action="/ShowBudget?task=calcBudget">
							<td class="noBborderStyle" colspan="4"><html:submit styleId="button" titleKey="main.general.button.Budget.calcBudget.alttext.text">
								<bean:message key="main.general.button.Budget.calcBudget.text" />
							</html:submit></td>
						</html:form>
					</td>
				</tr>
			</table>
	</c:if>
	<c:if test="${showResult}">
		<br>
		<html:form action="/ShowBudget?task=editStructure">
				<td class="noBborderStyle" colspan="4">
					<html:submit styleId="button" titleKey="main.general.button.Budget.editBudget.alttext.text">
							<bean:message key="main.general.button.Budget.editBudget.text" />
					</html:submit>
				</td>
		</html:form>
		<table class="center backgroundcolor">
			<tr>
				<th align="left" title="<bean:message
						key="main.general.Budget.result1.text" />"><b><bean:message key="main.general.Budget.result1.text" /></b></th>
				<th align="left" title="<bean:message
						key="main.general.Budget.result2.text" />"><b><bean:message key="main.general.Budget.result2.text" /></b></th>
			</tr>
			<c:forEach items="${changeFrom}" var="entry" varStatus="statusID">
				<tr class="${statusID.count%2==0 ? 'primarycolor' : 'secondarycolor'}">
					 <td nowrap>
					 	<c:out  value="${entry}"/>
					 </td>
					 <td nowrap>
					 	<c:out  value="${changeTo[statusID.index]}"/>
					 </td>
				</tr>
			</c:forEach>
			
		</table>
	</c:if>
	

</body>
</html:html>
