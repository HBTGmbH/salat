<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<tiles:useAttribute id="menuactive" name="menuactive"/>
<!doctype html>
<html lang="de">
<head>
    <meta charset="utf-8"/>
    <link rel="shortcut icon" type="image/x-icon" href="/favicon.ico"/>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"/>
    <meta http-equiv="X-UA-Compatible" content="ie=edge"/>
    <title>SALAT - <tiles:getAsString name="section" /> - <tiles:getAsString name="subsection" /></title>
    <!-- CSS files -->
    <link type="text/css" rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@tabler/core@1.0.0-beta20/dist/css/tabler.min.css"/>
    <link rel="stylesheet" type="text/css" href="https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@2.47.0/tabler-icons.min.css" />
    <link rel="stylesheet" type="text/css" href="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/css/select2.min.css" />
    <link rel="stylesheet" type="text/css" href="https://cdn.jsdelivr.net/npm/select2-bootstrap-5-theme@1.3.0/dist/select2-bootstrap-5-theme.min.css" />
    <link rel="stylesheet" href="<c:url value="/webjars/bootstrap-icons/font/bootstrap-icons.min.css"/>">
    <link rel="stylesheet" type="text/css" href="<c:url value="/style/tb.tabler.css" />" />
    <link rel="stylesheet" type="text/css" href="<c:url value="/style/tb-legacy.css" />" media="all" />
    <tiles:insert attribute="styles" ignore="true"/>
    <style>
      @import url('https://rsms.me/inter/inter.css');

      :root {
        --tblr-font-sans-serif: 'Inter Var', -apple-system, BlinkMacSystemFont, San Francisco, Segoe UI, Roboto, Helvetica Neue, sans-serif;
      }

      body {
        font-feature-settings: "cv03", "cv04", "cv11";
      }
    </style>
</head>
<body class="bi-layout-sidebar" data-bs-theme="dark">
<script src="/scripts/theme-selector.js"></script>
<div class="page">
    <!-- Sidebar -->
    <aside class="navbar navbar-vertical navbar-expand-md" data-bs-theme="dark" id="salat-nav">
        <div class="container-fluid">
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse"
                    data-bs-target="#sidebar-menu" aria-controls="sidebar-menu" aria-expanded="false"
                    aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            <h1 class="navbar-brand navbar-brand-autodark">
        <span>
          <img src="<c:url value="/images/hbt-logo.svg" />" alt="SALAT" class="navbar-brand-image"
               style="height: 24px">
          <h4 style="margin-block-end: 0"><bean:message key="main.general.application.title" /></h4>
        </span>
            </h1>
            <div class="collapse navbar-collapse" id="sidebar-menu">
                <ul class="navbar-nav pt-lg-3">
                    <li class="nav-item <c:if test="${menuactive == 'home'}">active</c:if>">
                        <a class="nav-link" href="/do/ShowWelcome">
                            <i class="nav-link-icon ti ti-home"></i>
                            <span class="nav-link-title">Home</span>
                        </a>
                    </li>
                    <li class="nav-item dropdown <c:if test="${menuactive == 'timereport'}">active</c:if>">
                        <a class="nav-link dropdown-toggle" href="#" data-bs-toggle="dropdown"
                           data-bs-auto-close="false" role="button">
                            <i class="nav-link-icon ti ti-clock"></i>
                            <span class="nav-link-title"><bean:message key="main.general.mainmenu.timereports.text" /></span>
                        </a>
                        <div class="dropdown-menu <c:if test="${menuactive == 'timereport'}">show</c:if>">
                            <div class="dropdown-menu-columns">
                                <div class="dropdown-menu-column">
                                    <html:link styleClass="dropdown-item" action="/CreateDailyReport">
                                        <bean:message key="main.general.mainmenu.newreport.text" />
                                    </html:link>
                                    <html:link styleClass="dropdown-item" action="/ShowDailyReport">
                                        <bean:message key="main.general.mainmenu.daily.text" />
                                    </html:link>
                                    <html:link styleClass="dropdown-item" action="/ShowMatrix">
                                        <bean:message key="main.general.mainmenu.matrixmenu.text" />
                                    </html:link>
                                    <c:if test="${not loginEmployee.restricted}">
                                        <html:link styleClass="dropdown-item" action="/ShowTraining">
                                            <bean:message key="main.general.mainmenu.training.text" />
                                        </html:link>
                                    </c:if>
                                    <html:link styleClass="dropdown-item" action="/ShowRelease">
                                        <bean:message key="main.general.mainmenu.release.title.text" />
                                    </html:link>
                                </div>
                            </div>
                        </div>
                    </li>
                    <c:if test="${not loginEmployee.restricted}">
                        <li class="nav-item dropdown <c:if test="${menuactive == 'employee'}">active</c:if>">
                            <a class="nav-link dropdown-toggle" href="#" data-bs-toggle="dropdown"
                               data-bs-auto-close="false" role="button">
                                <i class="nav-link-icon ti ti-users"></i>
                                <span class="nav-link-title"><bean:message key="main.general.mainmenu.employees.text" /></span>
                            </a>
                            <div class="dropdown-menu <c:if test="${menuactive == 'employee'}">show</c:if>">
                                <div class="dropdown-menu-columns">
                                    <div class="dropdown-menu-column">
                                        <html:link styleClass="dropdown-item" action="/ShowEmployee">
                                            <bean:message key="main.general.mainmenu.employees.text" />
                                        </html:link>
                                        <html:link styleClass="dropdown-item" action="/ShowEmployeecontract">
                                            <bean:message key="main.general.mainmenu.employeecontracts.text" />
                                        </html:link>
                                        <html:link styleClass="dropdown-item" action="/ShowEmployeeorder">
                                            <bean:message key="main.general.mainmenu.employeeorders.text" />
                                        </html:link>
                                        <html:link styleClass="dropdown-item" action="/ShowOvertime">
                                            <bean:message key="main.general.mainmenu.overtime.text" />
                                        </html:link>
                                    </div>
                                </div>
                            </div>
                        </li>
                        <li class="nav-item dropdown <c:if test="${menuactive == 'order'}">active</c:if>">
                            <a class="nav-link dropdown-toggle" href="#" data-bs-toggle="dropdown"
                               data-bs-auto-close="false" role="button">
                                <i class="nav-link-icon ti ti-file-invoice"></i>
                                <span class="nav-link-title"><bean:message key="main.general.mainmenu.orders.text" /></span>
                            </a>
                            <div class="dropdown-menu <c:if test="${menuactive == 'order'}">show</c:if>">
                                <div class="dropdown-menu-columns">
                                    <div class="dropdown-menu-column">
                                        <html:link styleClass="dropdown-item" action="/ShowCustomer">
                                            <bean:message key="main.general.mainmenu.customers.text" />
                                        </html:link>
                                        <html:link styleClass="dropdown-item" action="/ShowCustomerorder">
                                            <bean:message key="main.general.mainmenu.customerorders.text" />
                                        </html:link>
                                        <html:link styleClass="dropdown-item" action="/ShowSuborder">
                                            <bean:message key="main.general.mainmenu.suborders.text" />
                                        </html:link>
                                        <c:if test="${authorizedUser.backoffice}">
                                            <html:link styleClass="dropdown-item" action="/ShowInvoice">
                                                <bean:message key="main.general.mainmenu.invoice.title.text" />
                                            </html:link>
                                        </c:if>
                                        <c:if test="${authViewHelper.isReportMenuAvailable()}">
                                            <html:link styleClass="dropdown-item" action="/ShowReports">
                                                <bean:message key="main.general.mainmenu.reporting.text" />
                                            </html:link>
                                        </c:if>
                                    </div>
                                </div>
                            </div>
                        </li>
                    </c:if>

                </ul>
                <div class="card border-0">
                    <div class="card-body p-4 text-center">
                        <p class="text-secondary">Angemeldet als</p>
                        <span class="avatar avatar-xl mb-3 rounded ti ti-user-circle"></span>
                        <h3 class="m-0 mb-1">${loginEmployee.name}</h3>
                        <div class="text-secondary">${loginEmployee.sign}</div>
                        <div class="mt-3">
                            <span class="badge bg-purple-lt"><bean:message key="main.employee.status.${loginEmployee.status}" /></span>
                        </div>
                    </div>
                    <c:if test="${salatProperties.auth.logout.enabled}">
                        <div class="d-flex">
                            <a href="<c:out value="${salatProperties.auth.logout.logoutUrl}" />" class="card-btn"><!-- Download SVG icon from http://tabler-icons.io/i/mail -->
                                <svg xmlns="http://www.w3.org/2000/svg" class="icon icon-tabler icon-tabler-logout" width="44" height="44" viewBox="0 0 24 24" stroke-width="1.5" stroke="#2c3e50" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 10px">
                                    <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                                    <path d="M14 8v-2a2 2 0 0 0 -2 -2h-7a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h7a2 2 0 0 0 2 -2v-2" />
                                    <path d="M9 12h12l-3 -3" />
                                    <path d="M18 15l3 -3" />
                                </svg>
                                Logout</a>
                        </div>
                    </c:if>
                </div>
            </div>
        </div>
    </aside>
    <div class="page-wrapper">
        <!-- Page header -->
        <div class="page-header d-print-none">
            <div class="container-md">
                <div class="row g-2 align-items-center">
                    <div class="col">
                        <!-- Page pre-title -->
                        <div class="page-pretitle">
                            <tiles:getAsString name="section" />
                        </div>
                        <h2 class="page-title">
                            <tiles:getAsString name="subsection" />
                        </h2>
                    </div>
                    <!-- Page title actions -->
                    <div class="col-auto ms-auto d-print-none">
                        <div class="btn-list">
                            <button class="btn hide-theme-dark" aria-label="Enable dark mode" onclick="selectTheme('dark')">
                                <i class="ti ti-moon ti-size-3"></i>
                            </button>
                            <button class="btn hide-theme-light" aria-label="Enable light mode" onclick="selectTheme('light')">
                                <i class="ti ti-sun-high ti-size-3"></i>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- Page body -->
        <div class="page-body">
            <div class="container-md">
                <div class="row">
                    <div class="col-12 salat-body">
                        <tiles:insert attribute="content" />
                    </div>
                </div>
            </div>
        </div>
        <footer class="footer footer-transparent d-print-none">
            <div class="container-md">
                <div class="row text-center align-items-center flex-row-reverse">
                    <div class="col-lg-auto ms-lg-auto">
                        <ul class="list-inline list-inline-dots mb-0">
                            <li class="list-inline-item"><a href="https://hbteam.atlassian.net/wiki/spaces/SALAT/overview" target="_blank"
                                                            class="link-secondary"
                                                            rel="noopener">Documentation</a></li>
                            <li class="list-inline-item"><a href="https://github.com/HBTGmbH/salat"
                                                            target="_blank" class="link-secondary" rel="noopener"><i class="ti ti-brand-github-filled"></i> Github</a></li>
                        </ul>
                    </div>
                    <div class="col-12 col-lg-auto mt-3 mt-lg-0">
                        <ul class="list-inline list-inline-dots mb-0">
                            <li class="list-inline-item">
                                Mit <i class="ti ti-heart-filled" style="color: darkred"></i> von
                                <a href="https://hbt.de" target="_blank" class="link-secondary">HBT</a> erstellt.
                            </li>
                            <li class="list-inline-item text-body-tertiary">
                                Version:
                                <c:out value="${buildProperties.version}" />
                                <c:out value="${buildProperties.time}" />
                                <c:out value="${gitProperties.branch}" />
                                <c:out value="${gitProperties.shortCommitId}" />
                                Server Datum/Zeit:
                                <c:out value="${serverTimeHelper.serverTime}" />
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </footer>
    </div>
</div>
<!-- Tabler Core -->
<script src="https://cdn.jsdelivr.net/npm/jquery@3.5.0/dist/jquery.slim.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@tabler/core@1.0.0-beta20/dist/js/tabler.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/js/select2.min.js"></script>
<script>
  $(document).ready(function() {
    $(".form-select").select2({
      theme: 'bootstrap-5'
    });
  });
</script>
<script type="text/javascript" language="JavaScript">
  $(document).ready(function () {
    $(".make-select2").select2({
      dropdownAutoWidth: true,
      width: 'auto'
    });
  });
</script>
<c:if test="${salatProperties.auth.refresh.enabled}">
    <script type="text/javascript">
      setInterval(function() {
        let refreshUrl = "<c:out value="${salatProperties.auth.refresh.refreshUrl}" />";
        $.ajax(refreshUrl).done(function() {
          console.log("Token refresh completed successfully.");
        }).fail(function() {
          console.log("Token refresh failed. See application logs for details.");
        });
      }, 1000 * 60 * 2);
    </script>
</c:if>
<tiles:insert attribute="scripts" ignore="true" />
</body>
</html>