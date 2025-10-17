<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
    <head>
        <title><bean:message key="main.general.application.title" /> - Scheduled Report Jobs</title>
        <jsp:include flush="true" page="/head-includes.jsp" />
        <script type="text/javascript" language="JavaScript">
            function confirmDelete(form, id) {
                var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
                if (agree) {
                    form.action = "/do/DeleteScheduledReportJob?id=" + id;
                    form.submit();
                }
            }
        </script>
    </head>
    <body>
    <jsp:include flush="true" page="/menu.jsp">
        <jsp:param name="title" value="Menu" />
    </jsp:include>
    <br>
    <span style="font-size:14pt;font-weight:bold;"><br>Scheduled Report Jobs<br></span>
    <br>
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
            <td><c:out value="${job.cronExpression != null ? job.cronExpression : 'Daily 2:00 AM'}" /></td>
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
    
    <br>
    <html:link action="/ShowReports">
        <html:button property="back" styleId="button">
            Back to Reports
        </html:button>
    </html:link>
    
    <br><br><br>
    </body>
</html:html>
