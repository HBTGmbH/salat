<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<div class="container-fluid">
    <div class="row">
        <div class="col-xxl-2 col-lg-3 navigation">
            <h1>
                <button class="btn my-auto"><i class="bi bi-list" style="color: white"></i></button>
                <span><bean:message key="main.general.application.title" /></span>
            </h1>
            <div class="navigation-content">
                <ul class="list-unstyled float-none">
                    <p><bean:message key="main.general.mainmenu.timereports.text" /></p>
                    <li>
                        <html:link action="/CreateDailyReport">
                            <bean:message key="main.general.mainmenu.newreport.text" />
                        </html:link>
                    </li>
                    <li>
                        <html:link action="/ShowDailyReport">
                            <bean:message key="main.general.mainmenu.daily.text" />
                        </html:link>
                    </li>
                    <li>
                        <html:link action="/ShowMatrix">
                            <bean:message key="main.general.mainmenu.matrixmenu.text" />
                        </html:link>
                    </li>
                    <c:if test="${not authorizedUser.restricted}">
                        <li>
                            <html:link action="/ShowTraining">
                                <bean:message key="main.general.mainmenu.training.text" />
                            </html:link>
                        </li>
                    </c:if>
                    <li>
                        <html:link action="/ShowRelease">
                            <bean:message key="main.general.mainmenu.release.title.text" />
                        </html:link>
                    </li>
                </ul>
                <c:if test="${not loginEmployee.restricted}">
                    <ul class="list-unstyled">
                        <p><bean:message key="main.general.mainmenu.employees.text" /></p>
                        <li>
                            <html:link action="/ShowEmployee">
                                <bean:message key="main.general.mainmenu.employees.text" />
                            </html:link>
                        </li>
                        <li>
                            <html:link action="/ShowEmployeecontract">
                                <bean:message key="main.general.mainmenu.employeecontracts.text" />
                            </html:link>
                        </li>
                        <li>
                            <html:link action="/ShowEmployeeorder">
                                <bean:message key="main.general.mainmenu.employeeorders.text" />
                            </html:link>
                        </li>
                    </ul>
                    <ul class="list-unstyled">
                        <p><bean:message key="main.general.mainmenu.orders.text" /></p>
                        <li><html:link action="/ShowCustomer">
                            <bean:message key="main.general.mainmenu.customers.text" />
                        </html:link>
                        </li>
                        <li>
                            <html:link action="/ShowCustomerorder">
                                <bean:message key="main.general.mainmenu.customerorders.text" />
                            </html:link>
                        </li>
                        <li>
                            <html:link action="/ShowSuborder">
                                <bean:message key="main.general.mainmenu.suborders.text" />
                            </html:link>
                        </li>
                        <c:if test="${authorizedUser.backoffice}">
                            <li>
                                <html:link action="/ShowInvoice">
                                    <bean:message key="main.general.mainmenu.invoice.title.text" />
                                </html:link>
                            </li>
                        </c:if>
                    </ul>
                </c:if>
                <ul class="list-unstyled">
                    <p><bean:message key="main.general.mainmenu.management.text" /></p>
                    <li>
                        <html:link action="/ShowWelcome">
                            <bean:message key="main.general.mainmenu.overview.text" />
                        </html:link>
                    </li>
                    <li>
                        <html:link action="/ShowSettings">
                            <bean:message key="main.general.mainmenu.settings.text" />
                        </html:link>
                    </li>
                    <c:if test="${authorizedUser.admin}">
                        <li>
                            <html:link action="/ShowAdminOptions">
                                <bean:message key="adminarea.title" />
                            </html:link>
                        </li>
                    </c:if>
                </ul>
                <div style="margin-top: 20px;" class="user-login">
                    <p>Angemeldet als</p>
                    ${authorizedUser.name} (${authorizedUser.sign})<br/>
                    <html:link action="/LogoutEmployee">
                        <bean:message key="main.general.logout.text" />
                    </html:link>
                </div>
                <div style="width: 50%; margin-top: 100px; margin-bottom: 25px">
                    <img src="/images/HBT_Logo_RGB_negativ.svg" class="img-fluid" />
                </div>
                <div>
                    <small style="color: #8c8caa">
                        Git Commit Id: <c:out value="${gitProperties.shortCommitId}" /><br/>
                        Commit Time: <c:out value="${gitProperties.commitTime}" /><br/>
                        Datum/Zeit: <c:out value="${serverTimeHelper.serverTime}" />
                    </small>
                </div>
            </div>
        </div>
        <div class="col-xxl-2 col-lg-3">
            <h1>&nbsp;</h1>
        </div>
        <div class="col-xxl-10 col-lg-9 main-content">
