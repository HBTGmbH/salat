<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>

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
}
window.onload=startList;

//--><!]]></script>

<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />
<center>
<h1><bean:message key="main.general.mainmenu.menu.text" /></h1>
</center>
<div class="menu hiddencontent">
<ul id="nav">
	<li id="first"><bean:message key="main.general.mainmenu.timereports.text" />
		<ul>
			<li class="first">
				<html:link styleClass="menu" action="/ShowDailyReport">
					<bean:message key="main.general.mainmenu.daily.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowMonthlyReport">
					<bean:message key="main.general.mainmenu.matrix.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowRelease">
					<bean:message key="main.general.mainmenu.release.title.text" />
				</html:link>
			</li>
		</ul>
	</li>
	<li><bean:message key="main.general.mainmenu.employees.text" />
		<ul>
			<li class="first">
				<html:link styleClass="menu" action="/ShowEmployee">
					<bean:message key="main.general.mainmenu.employees.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowEmployeecontract">
					<bean:message key="main.general.mainmenu.employeecontracts.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowEmployeeorder">
					<bean:message key="main.general.mainmenu.employeeorders.text" />
				</html:link>
			</li>
		</ul>
	</li>
	<li><bean:message key="main.general.mainmenu.orders.text" />
		<ul>
			<li class="first">
				<html:link styleClass="menu" action="/ShowCustomer">
					<bean:message key="main.general.mainmenu.customers.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowCustomerorder">
					<bean:message key="main.general.mainmenu.customerorders.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowSuborder">
					<bean:message key="main.general.mainmenu.suborders.text" />
				</html:link>
			</li>
		</ul>
	</li>
	<li><bean:message key="main.general.mainmenu.management.text" />
		<ul>
			<li class="first">
				<html:link styleClass="menu" action="/ShowWelcome">
					<bean:message key="main.general.mainmenu.overview.text" />
				</html:link>
			</li>
			<li>
				<html:link styleClass="menu" action="/ShowSettings">
					<bean:message key="main.general.mainmenu.settings.text" />
				</html:link>
			</li>
		</ul>
	</li>
	<li id="last">
		<html:link action="/LogoutEmployee">
			<bean:message key="main.general.logout.text" /> (<c:out value="${loginEmployee.loginname}" />/<c:out value="${loginEmployee.status}" />)
		</html:link>
	</li>
</ul>
</div>
