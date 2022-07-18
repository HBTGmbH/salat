<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
    <head>

        <title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.reporting.text" /></title>
        <jsp:include flush="true" page="/head-includes.jsp" />
        <script type="text/javascript" language="JavaScript">

            function confirmDelete(form, id) {
                var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
                if (agree) {
                    form.action = "/do/DeleteReport?reportId=" + id;
                    form.submit();
                }
            }

            function execute(form, id) {
                form.action = "/do/ExecuteReport?reportId=" + id;
                form.submit();
            }

            function edit(form, id) {
                form.action = "/do/EditReport?reportId=" + id;
                form.submit();
            }

        </script>
    </head>
    <body>
    <jsp:include flush="true" page="/menu.jsp">
        <jsp:param name="title" value="Menu" />
    </jsp:include>
    <br>
    <span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.reporting.text" /><br></span>
    <br>
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
            <html:form action="/ExecuteReport">
                <td align="center"><html:image
                        onclick="execute(this.form, ${report.id})"
                        src="/images/red_circle.gif" altKey="main.reporting.button.execute.text" /></td>
            </html:form>
            <html:form action="/EditReport">
                <td align="center"><html:image
                        onclick="edit(this.form, ${report.id})"
                        src="/images/Edit.gif" altKey="main.reporting.button.edit.text" /></td>
            </html:form>
            <html:form action="/DeleteReport">
                <td align="center"><html:image
                        onclick="confirmDelete(this.form, ${report.id})"
                        src="/images/Delete.gif" altKey="main.reporting.button.delete.text" /></td>
            </html:form>
            </tr>
        </c:forEach>
        <tr>
            <html:form action="/CreateReport">
                <td class="noBborderStyle" colspan="4">
                    <html:submit styleId="button">
                        <bean:message key="main.reporting.button.create.text" />
                    </html:submit>
                </td>
            </html:form>
        </tr>
    </table>
    <br><br><br>
    </body>
</html:html>
