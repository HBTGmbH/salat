<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/tree" prefix="myjsp" %>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<tiles:insert definition="page">
	<tiles:put name="menuactive" direct="true" value="timereport" />
	<tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.timereports.text"/></tiles:put>
	<tiles:put name="subsection" direct="true"><bean:message key="main.general.mainmenu.release.title.text"/></tiles:put>
	<tiles:put name="scripts" direct="true">
		<script type="text/javascript" language="JavaScript">
			function confirmSendReleaseMail(form, sign) {
				var agree=confirm("<bean:message key="main.general.confirmmail.text" />");
				if (agree) {
					form.action = "/do/ShowRelease?task=sendreleasemail&sign="+sign;
					form.submit();
				}
			}

			function confirmSendAcceptanceMail(form, sign) {
				var agree=confirm("<bean:message key="main.general.confirmmail.text" />");
				if (agree) {
					form.action = "/do/ShowRelease?task=sendacceptancemail&sign="+sign;
					form.submit();
				}
			}



			function confirmRelease(form) {
				var agree=confirm("<bean:message key="main.general.confirmrelease.text" />");
				if (agree) {
					form.action = "/do/ShowRelease?task=release";
					form.submit();
				}
			}

			function confirmAcceptance(form) {
				var agree=confirm("<bean:message key="main.general.confirmacceptance.text" />");
				if (agree) {
					form.action = "/do/ShowRelease?task=accept";
					form.submit();
				}
			}

			function confirmReopen(form) {
				var agree=confirm("<bean:message key="main.general.confirmreopen.text" />");
				if (agree) {
					form.action = "/do/ShowRelease?task=reopen";
					form.submit();
				}
			}

			function setUpdateEmployeeContract(form) {
				form.action = "/do/ShowRelease?task=updateEmployee";
				form.submit();
			}

			function setUpdateSupervisor(form) {
				form.action = "/do/ShowRelease?task=updateSupervisor";
				form.submit();
			}

			$(document).ready(function() {
				$(".make-select2").select2({
					dropdownAutoWidth: true,
					width: 'auto'
				});
			});
		</script>
	</tiles:put>
	<tiles:put name="content" direct="true">
		<html:form action="/ShowRelease">
			<table border="0" cellspacing="0" cellpadding="2" class="center backgroundcolor">
				<c:if test="${isSupervisor or authorizedUser.manager}">
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
										<c:out value="${supervisor.name}" /> |
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
							<c:when test="${authorizedUser.manager or isSupervisor}">
								<html:select property="employeeContractId" styleClass="make-select2"
											 onchange="setUpdateEmployeeContract(this.form)">
									<html:option value="${loginEmployeeContract.id}">
										<c:out value="${loginEmployeeContract.employee.name}" /> |
										<c:out value="${loginEmployeeContract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
											value="${loginEmployeeContract.timeString}" />
										<c:if test="${loginEmployeeContract.openEnd}">
											<bean:message key="main.general.open.text" />
										</c:if>)
									</html:option>
									<c:forEach var="employeecontract" items="${employeecontracts}">
										<html:option value="${employeecontract.id}">
											<c:out value="${employeecontract.employee.name}" /> |
											<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out
												value="${employeecontract.timeString}" />
											<c:if test="${employeecontract.openEnd}">
												<bean:message key="main.general.open.text" />
											</c:if>)
										</html:option>
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
						test="${authorizedUser.manager || employeeContractId == loginEmployeeContractId}">
					<tr>
						<td align="left" class="noBborderStyle" height="10"></td>
					</tr>
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.release.until.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<input type="month" name="releaseDateString" value="<bean:write name="showReleaseForm" property="releaseDateString" />"/>
						</td>

						<td class="noBborderStyle">
							<html:submit onclick="confirmRelease(this.form);return false"
										 styleId="button">
								<bean:message key="main.general.button.release.text" />
							</html:submit>
						</td>
					</tr>
				</c:if>


				<!-- acceptance -->
				<c:if test="${isSupervisor || authorizedUser.manager}">
					<tr>
						<td align="left" class="noBborderStyle" height="30"></td>
					</tr>

					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.accept.until.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<input type="month" name="acceptanceDateString" value="<bean:write name="showReleaseForm" property="acceptanceDateString" />"  />
							<span style="color: red"><html:errors property="acceptancedate" /></span>
						</td>

						<td class="noBborderStyle">
							<html:submit onclick="confirmAcceptance(this.form);return false"
										 styleId="button">
								<bean:message key="main.general.button.accept.text" />
							</html:submit>
						</td>
					</tr>
				</c:if>


				<!-- reopen -->
				<c:if test="${authorizedUser.admin}">
					<tr>
						<td align="left" class="noBborderStyle" height="30"></td>
					</tr>

					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.release.reopen.until.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<input type="month" name="reopenDateString" value="<bean:write name="showReleaseForm" property="reopenDateString" />"  />
							<span style="color: red"><html:errors property="reopendate" /></span>
						</td>

						<td class="noBborderStyle">
							<html:submit onclick="confirmReopen(this.form);return false"
										 styleId="button">
								<bean:message key="main.general.button.reopen.text" />
							</html:submit>
						</td>
					</tr>
				</c:if>
			</table>

			<html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />

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
							<c:out value="${employeecontract.employee.name}" />
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

									<c:if test="${authorizedUser.manager}">
										<html:image title="Erinnerungsmail senden"
													onclick="confirmSendReleaseMail(this.form, '${employeecontract.employee.sign}');return false"
													src="/images/mail_icon_01.gif">
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
									<c:if test="${authorizedUser.manager && !employeecontract.releaseWarning}">
										<html:image title="Erinnerungsmail senden"
													onclick="confirmSendAcceptanceMail(this.form, '${employeecontract.employee.sign}');return false"
													src="/images/mail_icon_01.gif">
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
					</c:forEach>
				</table>
			</c:if>
		</html:form>
	</tiles:put>
</tiles:insert>