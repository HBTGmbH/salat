<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html" %>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@taglib uri="jakarta.tags.core" prefix="c" %>
<%@taglib uri="jakarta.tags.functions" prefix="fn" %>
<html>
<head>
    <title><bean:message key="main.general.application.title"/> - <bean:message
            key="main.general.mainmenu.matrix.title.text"/></title>
    <jsp:include flush="true" page="/head-includes.jsp"/>
    <link rel="stylesheet" type="text/css" href="<c:url value="/style/matrixprint.css" />"
          media="all"/>
    <link rel="stylesheet" type="text/css" href="<c:url value="/style/print.css" />" media="print"/>
    <style>
      th {
        background-color: white;
        color: black;
      }
    </style>
</head>
<body>
<form onsubmit="javascript:window.print();return false;">
    <div align="right"><input class="hiddencontent" type="submit"
                              value="Drucken"></div>
</form>
<table style="border:1px black solid;" class="matrix" width="100%">
    <tr class="matrix">
        <th class="matrix">
            <img src="<c:url value="/images/hbt-logo.svg" />" height="40px" style="padding: 5px"/>
        </th>
        <th class="matrix" colspan="${daysofmonth+1}">
            <bean:message key="main.matrixoverview.headline.tb.text"/><br/>
            <c:if test="${currentEmployee eq 'ALL EMPLOYEES'}">
                <bean:message key="main.matrixoverview.headline.allemployees.text"/>
            </c:if>
            <c:if test="${!(currentEmployee eq 'ALL EMPLOYEES')}">
                <c:out value="${currentEmployee}"/>
            </c:if>
            <br/>
            <bean:message key="${MonthKey}"/> <c:out value="${currentYear}"/>
        </th>
    </tr>

    <tr>
        <td class="matrix bold"><bean:message
                key="main.matrixoverview.table.description"/></td>
        <c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
            <td class="matrix bold${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}" style="text-align: center">
            &nbsp;<c:out value="${matrixdaytotal.dayString}"/>&nbsp;
            </td>
        </c:forEach>
        <td class="matrix bold" style="text-align: right"><bean:message
                key="main.matrixoverview.table.sum.text"/></td>
    </tr>

    <c:forEach var="matrixline" items="${matrixlines}">
        <tr class="matrix">
            <td class="matrix" style="border:1px black solid;"><c:out
                    value="${matrixline.subOrder.shortdescription}"/></td>
            <c:forEach var="bookingday" items="${matrixline.bookingDays}">
                <td class="matrix test${bookingday.publicHoliday ? ' holiday' : (bookingday.satSun ? ' weekend' : '')}"
                    align="right" style="border:1px black solid;">
                    <c:out value="${bookingday.durationString}"></c:out>
                </td>
            </c:forEach>
            <td class="matrix" align="right"><c:out
                    value="${matrixline.totalString}"></c:out></td>
        </tr>
    </c:forEach>
    <tr class="matrix">
        <td class="matrix bold"
            style="border-top:2px black solid;" align="right"><bean:message
                key="main.matrixoverview.table.overall.text"/></td>
        <c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
            <td class="matrix${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
                style="border-top:2px black solid"
                align="right">
                <c:out value="${matrixdaytotal.workingTimeString}"></c:out>
            </td>
        </c:forEach>
        <td class="matrix bold" style="border-top:2px black solid;"
            align="right"><c:out value="${totalworkingtimestring}"></c:out></td>
    </tr>
    <tr class="matrix">
        <td class="matrix" style="border-top: 2px black solid;" align="right"><bean:message
                key="main.matrixoverview.table.startofwork.text"/></td>
        <c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
            <td class="matrix${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
                style="border-top: 2px black solid;"
                align="right">
                <c:out value="${matrixdaytotal.zeroWorkingTime ? ' ' : matrixdaytotal.startOfWorkString}"></c:out>
            </td>
        </c:forEach>
        <td class="matrix" style="border-top: 2px black solid;" align="right">
            &nbsp;
        </td>
    </tr>
    <tr class="matrix">
        <td class="matrix" style="border-top: 1px black solid;" align="right"><bean:message
                key="main.matrixoverview.table.breakduration.text"/></td>
        <c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
            <td class="matrix${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
                style="border-top: 1x black solid;"
                align="right">
                <c:out value="${matrixdaytotal.zeroWorkingTime ? ' ' : matrixdaytotal.breakDurationString}"></c:out>
            </td>
        </c:forEach>
        <td class="matrix" align="right">&nbsp;</td>
    </tr>
    <tr class="matrix">
        <td class="matrix" align="right"><bean:message
                key="main.matrixoverview.table.endofwork.text"/></td>
        <c:forEach var="matrixdaytotal" items="${matrixdaytotals}">
            <td class="matrix${matrixdaytotal.publicHoliday ? ' holiday' : (matrixdaytotal.satSun ? ' weekend' : '')}"
                align="right">
                <c:out value="${matrixdaytotal.zeroWorkingTime ? ' ' : matrixdaytotal.endOfWorkString}"></c:out>
            </td>
        </c:forEach>
        <td class="matrix" align="right">
            &nbsp;
        </td>
    </tr>
    <tr class="matrix">
        <td class="matrix" colspan="${daysofmonth+3}">
            <table>
                <tr class="matrix">
                    <td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.actualtime.text" /></td>
                    <td class="matrix" style="border-style: none; text-align: right"><c:out	value="${totalworkingtimestring}"></c:out></td>
                </tr>
                    <tr class="matrix">
                        <td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.targettime.text" /></td>
                        <td class="matrix" style="border-style: none; text-align: right"><c:out	value="${totalworkingtimetargetstring}" /></td>
                    </tr>
                    <tr class="matrix">
                        <td class="matrix" style="border-style: none;"><bean:message key="main.matrixoverview.headline.difference.text" /></td>
                        <td class="matrix" style="border-style:none;text-align: right"><c:out value="${totalworkingtimediffstring}" /></td>
                    </tr>
            </table>
        </td>
    </tr>
</table>
<br>
<br>
</body>
</html>
