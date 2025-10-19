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
    <tiles:put name="menuactive" direct="true" value="order" />
    <tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.orders.text"/></tiles:put>
    <tiles:put name="subsection" direct="true">Scheduled Report Jobs</tiles:put>
    <tiles:put name="scripts" direct="true">
        <script type="text/javascript" language="JavaScript">
            function confirmDelete(form, id) {
                var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
                if (agree) {
                    form.action = "/do/DeleteScheduledReportJob?id=" + id;
                    form.submit();
                }
            }
        </script>
    </tiles:put>
    <tiles:put name="content" direct="true">
    <span style="color:red"><html:errors footer="<br>" /> </span>

    <table class="center backgroundcolor">
        <tr>
            <th align="left"><b>Name</b></th>
            <th align="left"><b>Report</b></th>
            <th align="left"><b>Recipients</b></th>
            <th align="left"><b>Enabled</b></th>
            <th align="left"><b>Schedule</b></th>
            <th align="left"><b>Last Updated</b></th>
            <th align="left" colspan="2">&nbsp;</th>
        </tr>

        <c:forEach var="job" items="${scheduledReportJobs}" varStatus="statusID">
            <c:choose>
                <c:when test="${statusID.count%2==0}">
                    <tr class="primarycolor">
                </c:when>
                <c:otherwise>
                    <tr class="secondarycolor">
                </c:otherwise>
            </c:choose>
            <td><c:out value="${job.name}" /></td>
            <td><c:out value="${job.reportDefinition.name}" /></td>
            <td><c:out value="${job.recipientEmails}" /></td>
            <td>
                <c:choose>
                    <c:when test="${job.enabled}">
                        <span style="color:green">✓ Yes</span>
                    </c:when>
                    <c:otherwise>
                        <span style="color:red">✗ No</span>
                    </c:otherwise>
                </c:choose>
            </td>
            <td>
                <c:out value="${empty job.cronExpression ? defaultCron : job.cronExpression}" />
                <br/>
                <small><c:out value="${cronDescriptions[job.id]}" /></small>
            </td>
            <td><java8:formatLocalDateTime value="${job.lastupdate}" /></td>
            <td align="center">
                <html:link action="/EditScheduledReportJob?id=${job.id}">
                    <html:image src="/images/Edit.gif" altKey="main.reporting.button.edit.text"/>
                </html:link>
            </td>
            <td align="center">
                <html:form action="/DeleteScheduledReportJob">
                    <html:image
                            onclick="confirmDelete(this.form, ${job.id})"
                            src="/images/Delete.gif"
                            altKey="main.reporting.button.delete.text"/>
                </html:form>
            </td>
            </tr>
        </c:forEach>
    </table>
    
    <html:form action="/CreateScheduledReportJob" method="GET">
        <html:submit styleId="button">
            Create New Scheduled Job
        </html:submit>
    </html:form>
    
    <br><br><br>
    </tiles:put>
</tiles:insert>
