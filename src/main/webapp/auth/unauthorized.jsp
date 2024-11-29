<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<html>
<head>
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.welcome.title.text" /></title>
<jsp:include flush="true" page="/head-includes.jsp" />
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<div style="width: 100%; text-align: center">
	<html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />
</div>
</body>
</html>
