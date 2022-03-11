<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
<head>
    <title>SALAT - chicoree edition - by kr@2022</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="/webjars/bootstrap/css/bootstrap.min.css"
          id="bootstrap-css"></link>
    <link rel="stylesheet" href="/webjars/bootstrap-icons/font/bootstrap-icons.css">
</head>
<body>
<div class="container">
    <html:messages id="msg" message="true">
        <div class="alert alert-danger" role="alert"><bean:write name="msg" /></div>
    </html:messages>
    <div class="container text-center h3">
        Welcome, ${loginEmployeeFirstname}!
    </div>
    <div class="card text-center">
        <div class="card-header">
            <div class="row justify-content-center text-center">
                <div class="col-2 display-2"><a href="/do/chicoree/ChangeDay?value=-1"><i class="bi bi-arrow-left-circle"></i></a></div>
                <div class="col-8">
                    <svg style="height: 100px; width: auto"
                         xmlns="http://www.w3.org/2000/svg"
                         aria-label="Calendar" role="img"
                         viewBox="0 0 512 512">
                        <path d="M512 455c0 32-25 57-57 57H57c-32 0-57-25-57-57V128c0-31 25-57 57-57h398c32 0 57 26 57 57z" fill="#e0e7ec"/>
                        <path d="M484 0h-47c2 4 4 9 4 14a28 28 0 1 1-53-14H124c3 4 4 9 4 14A28 28 0 1 1 75 0H28C13 0 0 13 0 28v157h512V28c0-15-13-28-28-28z" fill="#dd2f45"/>
                        <g fill="#f3aab9">
                            <circle cx="470" cy="142" r="14"/>
                            <circle cx="470" cy="100" r="14"/>
                            <circle cx="427" cy="142" r="14"/>
                            <circle cx="427" cy="100" r="14"/>
                            <circle cx="384" cy="142" r="14"/>
                            <circle cx="384" cy="100" r="14"/>
                        </g>
                        <text id="month"
                              x="32"
                              y="164"
                              fill="#fff"
                              font-family="monospace"
                              font-size="140px"
                              style="text-anchor: left">${dashboardDateMonth}</text>
                        <text id="day"
                              x="256"
                              y="400"
                              fill="#66757f"
                              font-family="monospace"
                              font-size="256px"
                              style="text-anchor: middle">${dashboardDateDay}</text>
                        <text id="weekday"
                              x="256"
                              y="480"
                              fill="#66757f"
                              font-family="monospace"
                              font-size="64px"
                              style="text-anchor: middle">${dashboardDateWeekday}</text>
                    </svg>
                </div>
                <div class="col-2 display-2"><a href="/do/chicoree/ChangeDay?value=1"><i class="bi bi-arrow-right-circle"></i></a></div>
            </div>
        </div>
        <div class="card-body">
            <c:if test="${timereportsExist}">
                <p class="card-text">You already reported ${timereportsDuration} today. Great!</p>
                <a href="timereport.jsp" class="btn btn-primary mb-4"><i class="bi bi-alarm"></i> Add more</a>
                <c:forEach var="timereport" items="${dashboardTimereports}">
                    <div class="card mb-4">
                        <div class="card-header">${timereport.title}</div>
                        <div class="card-body">
                            <h5 class="card-title">${timereport.duration}</h5>
                            <p class="card-text">${timereport.comment}</p>
                            <a href="/do/chicoree/EditTimereport?id=${timereport.id}" class="btn btn-primary me-5">
                                <i class="bi bi-pencil-square"></i>
                            </a>
                            <a href="/do/chicoree/DeleteTimereport?id=${timereport.id}" class="btn btn-danger"
                               onclick="return confirm('Do you really want to delete?')">
                                <i class="bi bi-trash"></i>
                            </a>
                        </div>
                    </div>
                </c:forEach>
            </c:if>
            <c:if test="${!timereportsExist}">
                <p class="card-text">You haven't reported any time today!</p>
                <a href="timereport.jsp" class="btn btn-primary"><i class="bi bi-alarm"></i> Start now</a>
            </c:if>
        </div>
    </div>
    <div class="card text-center mt-2 mb-4">
        <div class="card-header">Your time bank</div>
        <div class="card-body">
            <c:if test="${overtimeStatus.total.days != 0}">
                <h1>${overtimeStatus.total.days} day(s)</h1>
            </c:if>
            <c:if test="${overtimeStatus.total.hours != 0}">
                <h3>${overtimeStatus.total.hours} hour(s)</h3>
            </c:if>
            <c:if test="${overtimeStatus.total.minutes != 0}">
                <h3>${overtimeStatus.total.minutes} minute(s)</h3>
            </c:if>
            <p class="card-text text-secondary small">
                Based on time reports between
                <span class="text-nowrap">
                    <java8:formatLocalDate value="${overtimeStatus.total.begin}"/>
                    and <java8:formatLocalDate value="${overtimeStatus.total.end}"/>
                </span>
            </p>
        </div>
    </div>
    <div class="container text-center">
        <a href="/do/chicoree/Logout" class="btn btn-secondary mb-4"><i class="bi bi-power"></i> Logout</a>
    </div>
</div>
<script src="/webjars/jquery/jquery.min.js"></script>
<script src="/webjars/bootstrap/js/bootstrap.min.js"></script>
</body>
</html:html>
