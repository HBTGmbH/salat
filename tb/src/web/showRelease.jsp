<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message
	key="main.general.mainmenu.release.title.text" /></title>

<script type="text/javascript" language="JavaScript">
	
			
	function confirmRelease(form) {	
		var agree=confirm("<bean:message key="main.general.confirmrelease.text" />");
		if (agree) {
			form.action = "/tb/do/ShowRelease?task=release";
			form.submit();
		}
	}
	
	function refreshDate(form) {
		form.action = "/tb/do/ShowRelease?task=refreshDate";
		form.submit();
	}
	
	function setUpdateEmployeeContract(form) {
		form.action = "/tb/do/ShowRelease?task=updateEmployeeContract";
		form.submit();
	}
		
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
	<p>
	<h2><bean:message key="main.general.mainmenu.release.title.text" />:</h2>
	</p>
	<br>
	<html:form action="/ShowRelease">
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		
		<tr>
			<td align="left" class="noBborderStyle"><h3><bean:message
				key="main.release.releasetimeperiod.text" /></h3></td>
		</tr> 
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.release.employee.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<c:choose>
					<c:when test="${employeeAuthorized}">
						<html:select property="employeeContractId"
							onchange="setUpdateEmployeeContract(this.form)">
							<c:forEach var="employeecontract" items="${employeecontracts}" >
								<html:option value="<c:out value='${employeecontract.id}' />">
									<c:out value="${employeecontract.employee.name}" /> (<c:out value="${employeecontract.validFrom}" /> - <c:out value="${employeecontract.validUntil}" />)
								</html:option>							
							</c:forEach>
						</html:select> 
					</c:when>
					<c:otherwise>
						<c:out value="${loginEmployee.name}" />
					</c:otherwise>
				</c:choose>		
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.release.until.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<c:out value="${releasedUntil}" />	
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
						key="main.monthlyreport.daymonthyear.text" />:</b></td>
			<td align="left" class="noBborderStyle">				
				<html:select property="day">
					<html:options collection="days" property="value"
						labelProperty="label" />
				</html:select>
				
				<html:select property="month" onchange="refreshDate(this.form)">
					<html:option value="Jan">
						<bean:message key="main.timereport.select.month.jan.text" />
					</html:option>
					<html:option value="Feb">
						<bean:message key="main.timereport.select.month.feb.text" />
					</html:option>
					<html:option value="Mar">
						<bean:message key="main.timereport.select.month.mar.text" />
					</html:option>
					<html:option value="Apr">
						<bean:message key="main.timereport.select.month.apr.text" />
					</html:option>
					<html:option value="May">
						<bean:message key="main.timereport.select.month.may.text" />
					</html:option>
					<html:option value="Jun">
						<bean:message key="main.timereport.select.month.jun.text" />
					</html:option>
					<html:option value="Jul">
						<bean:message key="main.timereport.select.month.jul.text" />
					</html:option>
					<html:option value="Aug">
						<bean:message key="main.timereport.select.month.aug.text" />
					</html:option>
					<html:option value="Sep">
						<bean:message key="main.timereport.select.month.sep.text" />
					</html:option>
					<html:option value="Oct">
						<bean:message key="main.timereport.select.month.oct.text" />
					</html:option>
					<html:option value="Nov">
						<bean:message key="main.timereport.select.month.nov.text" />
					</html:option>
					<html:option value="Dec">
						<bean:message key="main.timereport.select.month.dec.text" />
					</html:option>
				</html:select> 
				
				<html:select property="year" onchange="refreshDate(this.form)">
					<html:options collection="years" property="value"
						labelProperty="label" />
				</html:select>
				<span style="color:red"><html:errors property="releasedate" /></span>
			</td>
		</tr>
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="confirmRelease(this.form);return false" styleId="button">
				<bean:message key="main.general.button.release.text" />
			</html:submit></td>
		</tr>
	</table>
	
	<!-- 
	<br>
	<table class="center backgroundcolor">
	<tr>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.customerorder.text" />"><b><bean:message
			key="main.suborder.customerorder.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.customerorder.text" />"><b><bean:message
			key="main.suborder.customerorder.text" /></b></th>
		<th align="left" title="<bean:message
			key="main.headlinedescription.suborders.customerorder.text" />"><b><bean:message
			key="main.suborder.customerorder.text" /></b></th>
	 -->	
</html:form>
</body>
</html>
