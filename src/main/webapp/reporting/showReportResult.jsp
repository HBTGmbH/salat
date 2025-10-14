<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
    <head>
        <title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.reporting.text" /></title>
        <jsp:include flush="true" page="/head-includes.jsp" />
    </head>
    <body>
    <jsp:include flush="true" page="/menu.jsp">
        <jsp:param name="title" value="Menu" />
    </jsp:include>
    <br>
    <span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.reporting.text" />: <c:out value="${report.name}" /><br></span>
    <br>
    <span style="color:red"><html:errors footer="<br>" /> </span>

    <div>
        <details>
            <summary>Show SQL</summary>
            <code>SQL: <c:out value="${report.sql}"/></code>
            <c:forEach var="parameter" items="${reportParameters}">
                <br><code><c:out value="${parameter.name}"/> (<c:out value="${parameter.type}"/>) =
                <c:out value="${parameter.value}"/></code>
            </c:forEach>
        </details>
    </div>

    <table class="center backgroundcolor">
        <tr>
            <c:forEach var="column" items="${reportResult.columnHeaders}" varStatus="statusID">
                <th align="left"><b><c:out value="${column.name}" /></b></th>
            </c:forEach>
        </tr>

        <c:forEach var="row" items="${reportResult.rows}" varStatus="statusID">
            <c:choose>
                <c:when test="${statusID.count%2==0}">
                    <tr class="primarycolor">
                </c:when>
                <c:otherwise>
                    <tr class="secondarycolor">
                </c:otherwise>
            </c:choose>
            <c:forEach var="column" items="${reportResult.columnHeaders}" varStatus="statusID">
                <td align="left"><b><c:out value="${row.columnValues[column.name].valueAsString}" /></b></td>
            </c:forEach>
            </tr>
        </c:forEach>
    </table>
    <html:form action="/ExecuteReport?task=export">
        <html:submit styleId="button">
            <bean:message key="main.reporting.button.export.text" />
        </html:submit>
        <html:hidden property="reportId" />
    </html:form>
    <br><br><br>
    </body>
</html:html>
