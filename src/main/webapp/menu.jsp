<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>

<script type="text/javascript"><!--//--><![CDATA[//><!--
startList = function() {
	if (document.all&&document.getElementById) {
		navRoot = document.getElementById("nav");
		for (i=0; i<navRoot.childNodes.length; i++) {
			node = navRoot.childNodes[i];
			if (node.nodeName=="LI") {
				node.onmouseover=function() {
					this.className+=" over";
				}
				node.onmouseout=function() {
					this.className=this.className.replace(" over", "");
				}
			}
		}
	}
	var err = '<%= session.getAttribute("errors") %>';
	if (err == 'true') {
		window.scrollTo(0,document.body.scrollHeight);
	}
}
window.onload=startList;

//--><!]]></script>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<table style="width:100%;">
  <tr>
    <td class="noBborderStyle" align="center" width="25%"></td>
    <td class="noBborderStyle" align="center" valign="top" width="50%" title="<bean:message key='main.general.mainmenu.menu.subtext' />"><span style="font-size:14pt;font-weight:bold;"><bean:message key="main.general.mainmenu.menu.text" /></span></td>
    <td class="noBborderStyle" align="right" valign="top" width="25%">
		<font size="1pt"> 
		   	<bean:resource id="versionTxt" name="/WEB-INF/classes/version.txt" />
		   	<bean:write name="versionTxt" />
		</font>
	</td>
  </tr>
</table>
<div class="menu hiddencontent">
<ul id="nav">
	<li id="first"><bean:message
		key="main.general.mainmenu.timereports.text" />
	<ul>
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
		<ul>
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
		</ul>
		</li>
		<li><bean:message key="main.general.mainmenu.orders.text" />
		<ul>
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
			<li><html:link styleClass="menu" action="/ShowTickets">
				<bean:message key="main.general.mainmenu.tickets.text" />
			</html:link></li>
			<li><html:link styleClass="menu" action="/ShowStatusReport">
				<bean:message key="main.general.mainmenu.statusreports.text" />
			</html:link></li>
			<li><html:link styleClass="menu" action="/ShowInvoice">
				<bean:message key="main.general.mainmenu.invoice.title.text" />
			</html:link></li>
			<!--  <li><html:link styleClass="menu" action="/ShowBudget">
				<bean:message key="main.general.mainmenu.budget.text" />
			</html:link></li>-->
		</ul>
		</li>
	</c:when>
	<c:otherwise>
		<li>&nbsp;</li>
		<li>&nbsp;</li>
	</c:otherwise>
	</c:choose>
	<li><bean:message key="main.general.mainmenu.management.text" />
	<ul>
		<li class="first"><html:link styleClass="menu"
			action="/ShowWelcome">
			<bean:message key="main.general.mainmenu.overview.text" />
		</html:link></li>
		<li><html:link styleClass="menu" action="/ShowSettings">
			<bean:message key="main.general.mainmenu.settings.text" />
		</html:link></li>
		<c:choose>
			<c:when test="${clientIntern}">
				<li><html:link styleClass="menu" target="_blank" href="http://wiki/mediawiki/index.php/Benutzerhandbuch_SALAT">
					<bean:message key="main.general.mainmenu.bhb.text" />
				</html:link></li>
			</c:when>
			<c:otherwise>
				<li><html:link styleClass="menu" target="_blank" href="https://wiki.hbt.de/mediawiki/index.php/Benutzerhandbuch_SALAT">
					<bean:message key="main.general.mainmenu.bhb.text" />
				</html:link></li>
			</c:otherwise>
		</c:choose>
		<c:if test="${loginEmployee.sign == 'adm'}">
			<li><html:link styleClass="menu" action="/ShowAdminOptions">
				<bean:message key="adminarea.title" />
			</html:link></li>
		</c:if>
	</ul>
	</li>
	<li id="last"><html:link action="/LogoutEmployee">
		<bean:message key="main.general.logout.text" /> (<c:out
			value="${loginEmployee.loginname}" />/<c:out
			value="${loginEmployee.status}" />)
	</html:link></li>
</ul>
</div>
<br>
<br>