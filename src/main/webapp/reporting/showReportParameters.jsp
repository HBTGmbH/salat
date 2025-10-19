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
    <tiles:put name="menuactive" direct="true" value="reporting" />
    <tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.reporting.text"/></tiles:put>
    <tiles:put name="subsection" direct="true"><bean:message key="main.reporting.parameters.text"/></tiles:put>
    <tiles:put name="content" direct="true">
    <span style="color:red"><html:errors footer="<br>" /> </span>

    <div>
        <details>
            <summary>Show SQL</summary>
            <code>SQL: <c:out value="${report.sql}"/></code>
        </details>
    </div>

    <html:form action="/ExecuteReport?task=setParameters">
        <table class="center backgroundcolor">
            <tr>
                <th align="left"><b><bean:message key="main.reporting.parameter.name.text" /></b></th>
                <th align="left"><b><bean:message key="main.reporting.parameter.type.text" /></b></th>
                <th align="left"><b><bean:message key="main.reporting.parameter.value.text" /></b></th>
            </tr>
            <tr>
                <td><html:text property="parameters[0].name" /></td>
                <td>
                    <html:select property="parameters[0].type">
                        <html:option value="string">string</html:option>
                        <html:option value="date">date</html:option>
                        <html:option value="number">number</html:option>
                    </html:select>
                </td>
                <td><html:text property="parameters[0].value" /></td>
            </tr>
            <tr>
                <td><html:text property="parameters[1].name" /></td>
                <td>
                    <html:select property="parameters[1].type">
                        <html:option value="string">string</html:option>
                        <html:option value="date">date</html:option>
                        <html:option value="number">number</html:option>
                    </html:select>
                </td>
                <td><html:text property="parameters[1].value" /></td>
            </tr>
            <tr>
                <td><html:text property="parameters[2].name" /></td>
                <td>
                    <html:select property="parameters[2].type">
                        <html:option value="string">string</html:option>
                        <html:option value="date">date</html:option>
                        <html:option value="number">number</html:option>
                    </html:select>
                </td>
                <td><html:text property="parameters[2].value" /></td>
            </tr>
            <tr>
                <td><html:text property="parameters[3].name" /></td>
                <td>
                    <html:select property="parameters[3].type">
                        <html:option value="string">string</html:option>
                        <html:option value="date">date</html:option>
                        <html:option value="number">number</html:option>
                    </html:select>
                </td>
                <td><html:text property="parameters[3].value" /></td>
            </tr>
            <tr>
                <td><html:text property="parameters[4].name" /></td>
                <td>
                    <html:select property="parameters[4].type">
                        <html:option value="string">string</html:option>
                        <html:option value="date">date</html:option>
                        <html:option value="number">number</html:option>
                    </html:select>
                </td>
                <td><html:text property="parameters[4].value" /></td>
            </tr>
        </table>
        <html:hidden property="reportId" />
        <html:submit>
            <bean:message key="main.reporting.button.execute.text" />
        </html:submit>
    </html:form>
    <br><br><br>
    </tiles:put>
</tiles:insert>
