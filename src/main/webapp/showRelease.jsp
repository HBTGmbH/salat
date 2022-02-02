<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<html:base />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link href="style/select2.min.css" rel="stylesheet" />
<link rel="shortcut icon" type="image/x-icon" href="favicon.ico" />
<script src="scripts/jquery-1.11.3.min.js"></script>
<script src="scripts/select2.full.min.js"></script>

<title><bean:message key="main.general.application.title" /> - <bean:message
	 key="main.general.mainmenu.release.title.text" /></title>

		<script type="text/javascript" language="JavaScript">
	
			
	function confirmSendReleaseMail(form, sign) {	
		var agree=confirm("<bean:message key="main.general.confirmmail.text" />");
		if (agree) {
			form.action = "do/ShowRelease?task=sendreleasemail&sign="+sign;
			form.submit();
		}
	}
	
	function confirmSendAcceptanceMail(form, sign) {	
		var agree=confirm("<bean:message key="main.general.confirmmail.text" />");
		if (agree) {
			form.action = "do/ShowRelease?task=sendacceptancemail&sign="+sign;
			form.submit();
		}
	}
	
	
	
	function confirmRelease(form) {	
		var agree=confirm("<bean:message key="main.general.confirmrelease.text" />");
		if (agree) {
			form.action = "do/ShowRelease?task=release";
			form.submit();
		}
	}
	
	function confirmAcceptance(form) {	
		var agree=confirm("<bean:message key="main.general.confirmacceptance.text" />");
		if (agree) {
			form.action = "do/ShowRelease?task=accept";
			form.submit();
		}
	}
	
	function confirmReopen(form) {	
		var agree=confirm("<bean:message key="main.general.confirmreopen.text" />");
		if (agree) {
			form.action = "do/ShowRelease?task=reopen";
			form.submit();
		}
	}
	
	function refreshDate(form) {
		form.action = "do/ShowRelease?task=refreshDate";
		form.submit();
	}
	
	function refreshAcceptanceDate(form) {
		form.action = "do/ShowRelease?task=refreshAcceptanceDate";
		form.submit();
	}
	
	function refreshReopenDate(form) {
		form.action = "do/ShowRelease?task=refreshReopenDate";
		form.submit();
	}
	
	function refreshMonth(form) {
		form.action = "do/ShowRelease?task=refreshDate&refreshMonth=true";
		form.submit();
	}
	
	function refreshAcceptanceMonth(form) {
		form.action = "do/ShowRelease?task=refreshAcceptanceDate&refreshMonth=true";
		form.submit();
	}
	
	function refreshReopenMonth(form) {
		form.action = "do/ShowRelease?task=refreshReopenDate&refreshMonth=true";
		form.submit();
	}	
	
	function setUpdateEmployeeContract(form) {
		form.action = "do/ShowRelease?task=updateEmployee";
		form.submit();
	}
	
	function setUpdateSupervisor(form) {
		form.action = "do/ShowRelease?task=updateSupervisor";
		form.submit();
	}

	$(document).ready(function() {
		$(".make-select2").select2({
			dropdownAutoWidth: true,
			width: 'element'
		});	
	});		
</script>

	</head>
	<body>
		<jsp:include flush="true" page="/menu.jsp">
			<jsp:param name="title" value="Menu" />
		</jsp:include>
		<br>

		<html:form action="/ShowRelease">
			<table border="0" cellspacing="0" cellpadding="2"
				class="center backgroundcolor">

				<tr>
					<td class="noBborderStyle" align="left">
						<span style="font-size: 14pt; font-weight: bold;"><bean:message
								key="main.general.mainmenu.release.title.text" />:&nbsp;&nbsp;
							<p>
						</span>
					</td>
				</tr>
				
				<c:if test="${isSupervisor or employeeAuthorized}">	
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.supervisor.text" />:&nbsp;&nbsp;</b>
						</td>
					
						<td align="left" class="noBborderStyle">
							<html:select property="supervisorId" value="${supervisorId}" 
								onchange="setUpdateSupervisor(this.form)" styleClass="make-select2">
								<html:option value="-1">
									<bean:message key="main.general.all.text" />
								</html:option>
								 <c:forEach var="supervisor" items="${supervisors}">
									<html:option value="${supervisor.id}">
										<c:out value="${supervisor.sign}" />
									</html:option>
								</c:forEach> 
							</html:select>
							<html:hidden property="supervisorId" /> 
						</td>
					</tr>
				</c:if> 
				
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.release.employee.text" />:</b>
					</td>
						
					<td align="left" class="noBborderStyle">
						<c:choose>
							<c:when test="${employeeAuthorized or isSupervisor}">
								<html:select property="employeeContractId" styleClass="make-select2"
									onchange="setUpdateEmployeeContract(this.form)">
									<html:option value="${loginEmployeeContract.id}">
										<c:out value="${loginEmployeeContract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
											value="${loginEmployeeContract.timeString}" />
										<c:if test="${loginEmployeeContract.openEnd}">
											<bean:message key="main.general.open.text" />
										</c:if>)
									</html:option>
									<c:forEach var="employeecontract" items="${employeecontracts}">
										<c:if
											test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
											<html:option value="${employeecontract.id}">
												<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
													value="${employeecontract.timeString}" />
												<c:if test="${employeecontract.openEnd}">
													<bean:message key="main.general.open.text" />
												</c:if>)
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
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.release.released.until.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<c:out value="${releasedUntil}" />
					</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.release.accepted.until.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<c:out value="${acceptedUntil}" />
					</td>
				</tr>

				<!-- release -->

				<c:if
					test="${employeeAuthorized || employeeContractId == loginEmployeeContractId}">
					<tr>
						<td align="left" class="noBborderStyle" height="10"></td>
					</tr>
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.release.until.text" />:</b>
						</td>
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
							<span style="color: red"><html:errors
									property="releasedate" />
							</span>
						</td>

						<td class="noBborderStyle">
							<html:submit onclick="confirmRelease(this.form);return false"
								styleId="button">
								<bean:message key="main.general.button.release.text" />
							</html:submit>
						</td>
					</tr>
					<br>
				</c:if>


				<!-- acceptance -->
				<c:if test="${isSupervisor || employeeAuthorized}">
					<tr>
						<td align="left" class="noBborderStyle" height="30"></td>
					</tr>

					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.accept.until.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:select property="acceptanceDay">
								<html:options collection="acceptanceDays" property="value"
									labelProperty="label" />
							</html:select>

							<html:select property="acceptanceMonth"
								onchange="refreshAcceptanceMonth(this.form)">
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

							<html:select property="acceptanceYear"
								onchange="refreshAcceptanceDate(this.form)">
								<html:options collection="years" property="value"
									labelProperty="label" />
							</html:select>
							<span style="color: red"><html:errors
									property="acceptancedate" />
							</span>
						</td>

						<td class="noBborderStyle">
							<html:submit onclick="confirmAcceptance(this.form);return false"
								styleId="button">
								<bean:message key="main.general.button.accept.text" />
							</html:submit>
						</td>
					</tr>
					<br>
				</c:if>


				<!-- reopen -->
				<c:if test="${loginEmployee.status == 'adm'}">
					<tr>
						<td align="left" class="noBborderStyle" height="30"></td>
					</tr>

					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.reopen.until.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:select property="reopenDay">
								<html:options collection="reopenDays" property="value"
									labelProperty="label" />
							</html:select>

							<html:select property="reopenMonth"
								onchange="refreshReopenMonth(this.form)">
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

							<html:select property="reopenYear"
								onchange="refreshReopenDate(this.form)">
								<html:options collection="years" property="value"
									labelProperty="label" />
							</html:select>
							<span style="color: red"><html:errors
									property="reopendate" />
							</span>
						</td>

						<td class="noBborderStyle">
							<html:submit onclick="confirmReopen(this.form);return false"
								styleId="button">
								<bean:message key="main.general.button.reopen.text" />
							</html:submit>
						</td>
					</tr>
				</c:if>
				<br>
			</table>

			<!-- overview table -->
			<br>
			<br>
<%--	Benachrichtigung �ber emailvesand		--%>
			<div style="font-size: 12pt;"><i><c:out value="${actionInfo}" />&nbsp;</i></div>
			<c:if test="${not loginEmployee.restricted}">
			<br>

			<table class="center backgroundcolor">
				<tr>
					<td colspan="2" align="left" class="noBborderStyle">
						<h3>
							<bean:message key="main.release.overview.text" />
						</h3>
					</td>
				</tr>
				<tr>
					<th align="left">
						<b> <bean:message key="main.employeeorder.employee.text" />
						</b>
					</th>
					<th align="left">
						<b> <bean:message key="main.release.employee.text" />
						</b>
					</th>
					<th align="left">
						<b> <bean:message key="main.release.timeperiod.text" />
						</b>
					</th>
					<th align="left">
						<b> <bean:message key="main.release.released.until.text" />
						</b>
					</th>
					<th align="left">
						<b> <bean:message key="main.employeecontract.supervisor.text" />
						</b>
					</th>
					<th align="left">
						<b> <bean:message key="main.release.accepted.until.text" />
						</b>
					</th>
					<!-- <th align="left">
						Buchungen pr�fen
					</th> -->
				</tr>
				<c:forEach var="employeecontract" items="${employeecontracts}"
					varStatus="statusID">
					<c:if
						test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
						<c:choose>
							<c:when test="${statusID.count%2==0}">
								<tr class="primarycolor">
							</c:when>
							<c:otherwise>
								<tr class="secondarycolor">
							</c:otherwise>
						</c:choose>
						<td>
							<c:out value="${employeecontract.employee.sign}" />
						</td>
						<td>
							<%-- <html:link title="Buchungen pr�fen" 
							href="do/ShowDailyReport?currentEmployeeContract='${employeecontract}'">
							<font color="blue">--%>
							<c:out value="${employeecontract.employee.name}" />
							<%-- </font>
							</html:link> --%>
						</td>
						<td align="left">
							<c:out value="${employeecontract.timeString}" />
							<c:if test="${employeecontract.openEnd}">
								<bean:message key="main.general.open.text" />
							</c:if>
						</td>
						<td align="center">
							<c:choose>
								<c:when test="${employeecontract.releaseWarning}">
									<font color="red"><c:out
											value="${employeecontract.reportReleaseDateString}" />
									</font>

									<c:if test="${loginEmployee.status == 'pv' || loginEmployee.status == 'adm'}">
										<html:image title="Erinnerungsmail senden"
											onclick="confirmSendReleaseMail(this.form, '${employeecontract.employee.sign}');return false"
											src="images/mail_icon_01.gif">
											<font color="red"><c:out
													value="${employeecontract.reportReleaseDateString}" />
											</font>
										</html:image>
									</c:if>
								</c:when>
								<c:otherwise>
									<c:out value="${employeecontract.reportReleaseDateString}" />
								</c:otherwise>
							</c:choose>
						</td>
						<td align="center">
						  <c:out value="${employeecontract.supervisor.sign}" />&nbsp;
						</td>
						<td align="center">
							<c:choose>
								<c:when test="${employeecontract.acceptanceWarning}">
									<font color="red"><c:out value="${employeecontract.reportAcceptanceDateString}" />
									</font>
									<c:if test="${(loginEmployee.status == 'pv' || loginEmployee.status == 'adm') && !employeecontract.releaseWarning}">
										<html:image title="Erinnerungsmail senden"
											onclick="confirmSendAcceptanceMail(this.form, '${employeecontract.employee.sign}');return false"
											src="images/mail_icon_01.gif">
											<font color="red"><c:out
													value="${employeecontract.reportReleaseDateString}" />
											</font>
										</html:image>
									</c:if>
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
			</c:if>
		</html:form>
	</body>
</html>
