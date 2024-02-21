<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<script type="text/javascript">
	$(function() {
		var err = '<%= session.getAttribute("errors") %>';
		if (err == 'true') {
			window.scrollTo(0,document.body.scrollHeight);
		}
		$("#nav > li").click(function(){ $(this).toggleClass("over") });
	});
</script>

<table style="width:100%;">
  <tr>
    <td class="noBborderStyle" align="left" width="25%">
		<img src="/images/hbt-logo.svg" height="40px" />
	</td>
    <td class="noBborderStyle" align="center" valign="middle" width="50%" title="<bean:message key='main.general.mainmenu.menu.subtext' />">
		<span style="font-size:14pt;font-weight:bold;">
			SALAT<span class="sub-title">: System zum Abrechnen von Leistungen, Arbeitszeiten und T&auml;tigkeiten</span>
		</span>
	</td>
    <td class="noBborderStyle" align="right" valign="top" width="25%">
		<font size="1pt">
		   	<c:out value="${buildProperties.version}" />
			<c:out value="${buildProperties.time}" />
			<br/>
			<c:out value="${gitProperties.branch}" />
			<c:out value="${gitProperties.shortCommitId}" />
			<br/>
			Server Datum/Zeit: <c:out value="${serverTimeHelper.serverTime}" />
		</font>
	</td>
  </tr>
</table>
<div class="menu hiddencontent" style="padding-top: 5px">
<ul id="nav">
	<li id="first"><bean:message
		key="main.general.mainmenu.timereports.text" />
	<ul style="width: 100%">
		<li class="first"><html:link styleClass="menu"
			action="/CreateDailyReport">
			<bean:message key="main.general.mainmenu.newreport.text" />
		</html:link></li>
		<li><html:link styleClass="menu"
			action="/ShowDailyReport">
			<bean:message key="main.general.mainmenu.daily.text" />
		</html:link></li>
		<li><html:link styleClass="menu" action="/ShowMatrix">
			<bean:message key="main.general.mainmenu.matrixmenu.text" />
		</html:link></li>
		<c:if test="${not loginEmployee.restricted}"><li><html:link styleClass="menu" action="/ShowTraining">
			<bean:message key="main.general.mainmenu.training.text" />
		</html:link></li></c:if>
		<li><html:link styleClass="menu" action="/ShowRelease">
			<bean:message key="main.general.mainmenu.release.title.text" />
		</html:link></li>
	</ul>
	</li>
	<c:choose>
	<c:when test="${not loginEmployee.restricted}">
		<li><bean:message key="main.general.mainmenu.employees.text" />
		<ul style="width: 100%">
			<li class="first"><html:link styleClass="menu"
				action="/ShowEmployee">
				<bean:message key="main.general.mainmenu.employees.text" />
			</html:link></li>
			<li><html:link styleClass="menu" action="/ShowEmployeecontract">
				<bean:message key="main.general.mainmenu.employeecontracts.text" />
			</html:link></li>
			<li><html:link styleClass="menu" action="/ShowEmployeeorder">
				<bean:message key="main.general.mainmenu.employeeorders.text" />
			</html:link></li>
			<li class="first"><html:link styleClass="menu" action="/ShowOvertime">
				<bean:message key="main.general.mainmenu.overtime.text" />
			</html:link></li>
		</ul>
		</li>
		<li><bean:message key="main.general.mainmenu.orders.text" />
		<ul style="width: 100%">
			<li class="first"><html:link styleClass="menu"
				action="/ShowCustomer">
				<bean:message key="main.general.mainmenu.customers.text" />
			</html:link></li>
			<li><html:link styleClass="menu" action="/ShowCustomerorder">
				<bean:message key="main.general.mainmenu.customerorders.text" />
			</html:link></li>
			<li><html:link styleClass="menu" action="/ShowSuborder">
				<bean:message key="main.general.mainmenu.suborders.text" />
			</html:link></li>
			<c:if test="${authorizedUser.backoffice}">
				<li><html:link styleClass="menu" action="/ShowInvoice">
					<bean:message key="main.general.mainmenu.invoice.title.text" />
				</html:link></li>
			</c:if>
			<c:if test="${authViewHelper.isReportMenuAvailable()}">
				<li><html:link styleClass="menu" action="/ShowReports">
					<bean:message key="main.general.mainmenu.reporting.text" />
				</html:link></li>
			</c:if>
		</ul>
		</li>
	</c:when>
	<c:otherwise>
		<li>&nbsp;</li>
		<li>&nbsp;</li>
	</c:otherwise>
	</c:choose>
	<li><bean:message key="main.general.mainmenu.management.text" />
	<ul style="width: 100%">
		<li class="first"><html:link styleClass="menu" action="/ShowWelcome">
			<bean:message key="main.general.mainmenu.overview.text" />
		</html:link></li>
		<li><html:link styleClass="menu" action="/ShowSettings">
			<bean:message key="main.general.mainmenu.settings.text" />
		</html:link></li>
	</ul>
	</li>
	<li id="last">
		Angemeldet als <c:out
			value="${loginEmployee.loginname}" />/<c:out
			value="${loginEmployee.status}" />
	</li>
</ul>
</div>
<br>
<br>
