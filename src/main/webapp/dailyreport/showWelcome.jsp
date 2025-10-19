<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<tiles:insert definition="page">
    <tiles:put name="menuactive" direct="true" value="home" />
    <tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.welcome.title.text"/></tiles:put>
    <tiles:put name="subsection" direct="true"><bean:message key="main.general.mainmenu.overview.text"/></tiles:put>
    <tiles:put name="scripts" direct="true">
        <script type="text/javascript" language="JavaScript">
          function setUpdate(form) {
            form.action = "/do/ShowWelcome?task=refresh";
            form.submit();
          }
          function switchLogin(form) {
            form.action = "/do/ShowWelcome?task=switch-login";
            form.submit();
          }
        </script>
    </tiles:put>
    <tiles:put name="content" direct="true">
        <div style="width: 100%; text-align: center">
            <h1>Probier die neue <a href="/api/doc/swagger-ui/index.html" style="color:black">REST API</a>
                aus!</h1>
        </div>
        <br>
        <html:form action="/ShowWelcome">
            <html:select property="employeeContractId" onchange="setUpdate(this.form)" styleClass="make-select2">
            <c:forEach var="employeecontract" items="${employeecontracts}" >
                <html:option value="${employeecontract.id}">
                    <c:out value="${employeecontract.employee.name}" /> |
                    <c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" /><c:if
                        test="${employeecontract.openEnd}"><bean:message key="main.general.open.text" /></c:if>)
                </html:option>
            </c:forEach>
            </html:select>
            <c:if test="${loginEmployees.size() > 1}">
                <html:select property="loginEmployeeId" onchange="switchLogin(this.form)" styleClass="make-select2">
                    <c:forEach var="employee" items="${loginEmployees}" >
                        <html:option value="${employee.id}">
                            <c:out value="${employee.name}" /> |
                            <c:out value="${employee.sign}" />
                        </html:option>
                    </c:forEach>
                </html:select>
            </c:if>
        </html:form>
        <br/>
        <%--
        <c:if test="${welcomeViewHelper.displayEmployeeInfo}">
	<jsp:include flush="true" page="/info2.jsp">
		<jsp:param name="info" value="Info" />
	</jsp:include>
</c:if>
        --%>
        <tiles:insert definition="info2" flush="false" />
        <br>
        <!-- warnings -->
        <c:if test="${warningsPresent}">
            <table border="0" cellspacing="0" cellpadding="2" width="100%"
                   class="center backgroundcolor">
                <tr>
                    <th align="left" colspan="2">
                        <b><bean:message key="main.info.headline.warning"/></b>
                    </th>
                </tr>
                <c:forEach var="warning" items="${warnings}">
                    <tr>
                        <td class="noBborderStyle" align="left" width="5%" nowrap="nowrap">
                            <span style="color:red"><c:out value="${warning.sort}"/>:</span>
                        </td>
                        <td class="noBborderStyle" align="left">
                            <html:link style="color:red" href="${warning.link}"><c:out
                                    value="${warning.text}"/></html:link>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </c:if>
    </tiles:put>
</tiles:insert>