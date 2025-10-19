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
    <tiles:put name="subsection" direct="true"><bean:message key="main.general.overview.text"/></tiles:put>
    <tiles:put name="scripts" direct="true">
        <script type="text/javascript" language="JavaScript">

            function confirmDelete(form, id) {
                var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
                if (agree) {
                    form.action = "/do/DeleteReport?reportId=" + id;
                    form.submit();
                }
            }

        </script>
            </tiles:put>
            <tiles:put name="content" direct="true">
    <span style="color:red"><html:errors footer="<br>" /> </span>

    <table class="center backgroundcolor">
        <tr>
            <th align="left"><b><bean:message key="main.reporting.name.text" /></b></th>
            <th align="left"><b><bean:message key="main.reporting.lastupdate.text" /></b></th>
            <th align="left"><b><bean:message key="main.reporting.lastupdatedby.text" /></b></th>
            <th align="left" colspan="3">&nbsp;</th>
        </tr>

        <c:forEach var="report" items="${reportDescriptions}" varStatus="statusID">
            <c:choose>
                <c:when test="${statusID.count%2==0}">
                    <tr class="primarycolor">
                </c:when>
                <c:otherwise>
                    <tr class="secondarycolor">
                </c:otherwise>
            </c:choose>
            <td><c:out value="${report.name}" /></td>
            <td><java8:formatLocalDateTime value="${report.lastupdate}" /></td>
            <td><c:out value="${report.lastupdatedby}" /></td>
            <td align="center">
                <html:link action="/ExecuteReport?reportId=${report.id}">
                    <html:image src="/images/red_circle.gif" altKey="main.reporting.button.execute.text"/>
                </html:link>
            </td>
            <td align="center">
                <c:if test="${reportAuthViewHelper.isAuth(report,'READ')}">
                    <html:link action="/EditReport?reportId=${report.id}">
                        <html:image src="/images/Edit.gif" altKey="main.reporting.button.edit.text"/>
                    </html:link>
                </c:if>
            </td>
            <td align="center">
                <c:if test="${reportAuthViewHelper.isAuth(report,'DELETE')}">
                    <html:form action="/DeleteReport">
                        <html:image
                                onclick="confirmDelete(this.form, ${report.id})"
                                src="/images/Delete.gif"
                                altKey="main.reporting.button.delete.text"/>
                    </html:form>
                </c:if>
            </td>
            </tr>
        </c:forEach>
    </table>
    <c:if test="${reportAuthViewHelper.mayCreateNewReports()}">
        <html:form action="/CreateReport" method="GET">
            <html:submit styleId="button">
                <bean:message key="main.reporting.button.create.text" />
            </html:submit>
        </html:form>
    </c:if>
    <br><br><br>
    </tiles:put>
</tiles:insert>
