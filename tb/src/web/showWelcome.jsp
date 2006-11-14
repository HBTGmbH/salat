<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>HBT TB Welcome</title>
</head>
<body>
<jsp:include flush="true" page="/menu.jsp">
	<jsp:param name="title" value="Menu" />
</jsp:include>
<h2 style="color: black"><bean:message
	key="main.general.mainmenu.welcome.text" /></h2>
<br>
</body>
</html>
