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
	
	function confirmAcceptance(form) {	
		var agree=confirm("<bean:message key="main.general.confirmacceptance.text" />");
		if (agree) {
			form.action = "/tb/do/ShowRelease?task=accept";
			form.submit();
		}
	}
	
	function confirmReopen(form) {	
		var agree=confirm("<bean:message key="main.general.confirmreopen.text" />");
		if (agree) {
			form.action = "/tb/do/ShowRelease?task=reopen";
			form.submit();
		}
	}
	
	function refreshDate(form) {
		form.action = "/tb/do/ShowRelease?task=refreshDate";
		form.submit();
	}
	
	function refreshAcceptanceDate(form) {
		form.action = "/tb/do/ShowRelease?task=refreshAcceptanceDate";
		form.submit();
	}
	
	function refreshReopenDate(form) {
		form.action = "/tb/do/ShowRelease?task=refreshReopenDate";
		form.submit();
	}
	
	function refreshMonth(form) {
		form.action = "/tb/do/ShowRelease?task=refreshDate&refreshMonth=true";
		form.submit();
	}
	
	function refreshAcceptanceMonth(form) {
		form.action = "/tb/do/ShowRelease?task=refreshAcceptanceDate&refreshMonth=true";
		form.submit();
	}
	
	function refreshReopenMonth(form) {
		form.action = "/tb/do/ShowRelease?task=refreshReopenDate&refreshMonth=true";
		form.submit();
	}	
	
	function setUpdateEmployeeContract(form) {
		form.action = "/tb/do/ShowRelease?task=updateEmployee";
		form.submit();
	}
		
</script>

</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
	
	<html:form action="/ShowRelease">
	<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">
			<tr><td class="noBborderStyle" align="left">
			<span style="font-size:14pt;font-weight:bold;"><bean:message key="main.general.mainmenu.release.title.text" />:&nbsp;&nbsp;<p></span>
			</td></tr><tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.release.employee.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<c:choose>
					<c:when test="${employeeAuthorized}">
						<html:select property="employeeContractId"
							onchange="setUpdateEmployeeContract(this.form)">
							<c:forEach var="employeecontract" items="${employeecontracts}" >
								<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
									<html:option value="${employeecontract.id}">
										<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if 
											test="${employeecontract.openEnd}"><bean:message 
											key="main.general.open.text" /></c:if>)
									</html:option>
								</c:if>							
							</c:forEach>
						</html:select>
						<html:hidden property="employeeContractId" /> 
					</c:when>
					<c:otherwise>
						<c:out value="${loginEmployee.name}" />
					</c:otherwise>
				</c:choose>		
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.release.released.until.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<c:out value="${releasedUntil}" />	
			</td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.release.accepted.until.text" />:</b></td>
			<td align="left" class="noBborderStyle">
				<c:out value="${acceptedUntil}" />	
			</td>
		</tr>
		
		<!-- release -->

		<c:if test="${employeeAuthorized || employeeContractId == loginEmployeeContractId}">
			<tr>
				<td align="left" class="noBborderStyle" height="10"></td>
			</tr>
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
						key="main.release.release.until.text" />:</b></td>
				<td align="left" class="noBborderStyle">				
					<html:select property="day">
						<html:options collection="days" property="value"
							labelProperty="label" />
					</html:select>
				
					<html:select property="month" onchange="refreshMonth(this.form)">
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

				<td class="noBborderStyle"><html:submit
					onclick="confirmRelease(this.form);return false" styleId="button">
					<bean:message key="main.general.button.release.text" />
				</html:submit></td>
			</tr>			
			<br>
		</c:if>
		
		
		<!-- acceptance -->
		<c:if test="${employeeAuthorized}">
			<tr>
				<td align="left" class="noBborderStyle" height="30"></td>
			</tr>

			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
						key="main.release.accept.until.text" />:</b></td>
				<td align="left" class="noBborderStyle">				
					<html:select property="acceptanceDay">
						<html:options collection="acceptanceDays" property="value"
							labelProperty="label" />
					</html:select>
				
					<html:select property="acceptanceMonth" onchange="refreshAcceptanceMonth(this.form)">
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
				
					<html:select property="acceptanceYear" onchange="refreshAcceptanceDate(this.form)">
						<html:options collection="years" property="value"
							labelProperty="label" />
					</html:select>
					<span style="color:red"><html:errors property="acceptancedate" /></span>
				</td>

				<td class="noBborderStyle"><html:submit
					onclick="confirmAcceptance(this.form);return false" styleId="button">
					<bean:message key="main.general.button.accept.text" />
				</html:submit></td>
			</tr>
			<br>
		</c:if>
		
		
		<!-- reopen -->
		<c:if test="${loginEmployee.sign == 'adm'}">
			<tr>
				<td align="left" class="noBborderStyle" height="30"></td>
			</tr>
		
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
						key="main.release.reopen.until.text" />:</b></td>
				<td align="left" class="noBborderStyle">				
					<html:select property="reopenDay">
						<html:options collection="reopenDays" property="value"
							labelProperty="label" />
					</html:select>
				
					<html:select property="reopenMonth" onchange="refreshReopenMonth(this.form)">
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
				
					<html:select property="reopenYear" onchange="refreshReopenDate(this.form)">
						<html:options collection="years" property="value"
							labelProperty="label" />
					</html:select>
					<span style="color:red"><html:errors property="reopendate" /></span>
				</td>

				<td class="noBborderStyle"><html:submit
					onclick="confirmReopen(this.form);return false" styleId="button">
					<bean:message key="main.general.button.reopen.text" />
				</html:submit></td>
			</tr>
		</c:if>
		<br>
	</table>
	
	<!-- overview table --> 
	<br>
	<br>
	<br>
	<table class="center backgroundcolor">
		<tr>
			<td colspan="2" align="left" class="noBborderStyle"><h3><bean:message
				key="main.release.overview.text" /></h3></td>
		</tr>
		<tr>
			<th align="left"><b>
				<bean:message key="main.employeeorder.employee.text" /></b></th>
			<th align="left"><b>
				<bean:message key="main.release.employee.text" /></b></th>
			<th align="left"><b>
				<bean:message key="main.release.timeperiod.text" /></b></th>
			<th align="left"><b>
				<bean:message key="main.release.released.until.text" /></b></th>
			<th align="left"><b>
				<bean:message key="main.release.accepted.until.text" /></b></th>
		</tr>		
		<c:forEach var="employeecontract" items="${employeecontracts}" varStatus="statusID">
			<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
				<c:choose>
					<c:when test="${statusID.count%2==0}">
						<tr class="primarycolor">
					</c:when>
					<c:otherwise>
						<tr class="secondarycolor">
					</c:otherwise>
				</c:choose>
					<td><c:out value="${employeecontract.employee.sign}" /></td>
					<td><c:out value="${employeecontract.employee.name}" /></td>
					<td align="left"><c:out value="${employeecontract.timeString}" /><c:if 
						test="${employeecontract.openEnd}"><bean:message 
						key="main.general.open.text" /></c:if></td>
					<td align="center">
						<c:choose>
							<c:when test="${employeecontract.releaseWarning}">
								<font color="red"><c:out value="${employeecontract.reportReleaseDateString}" /></font>		
							</c:when>
							<c:otherwise>
								<c:out value="${employeecontract.reportReleaseDateString}" />
							</c:otherwise>
						</c:choose>
					</td>
					<td align="center">
						<c:choose>
							<c:when test="${employeecontract.acceptanceWarning}">
								<font color="red"><c:out value="${employeecontract.reportAcceptanceDateString}" /></font>		
							</c:when>
							<c:otherwise>
								<c:out value="${employeecontract.reportAcceptanceDateString}" />
							</c:otherwise>
						</c:choose>
					</td>
				</tr>
			</c:if>
		</c:forEach>
	</table>
	 	
</html:form>
</body>
</html>
