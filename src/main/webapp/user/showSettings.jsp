<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<html>
<head>
	<title><bean:message key="main.general.application.title" /> -
	<bean:message key="main.general.mainmenu.settings.title.text" /></title>
	<jsp:include flush="true" page="/head-includes.jsp" />
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<br>
<span style="font-size:14pt;font-weight:bold;"><br>
<bean:message key="main.general.mainmenu.settings.title.text" />:<br>
</span>
<br>

</body>
</html>
