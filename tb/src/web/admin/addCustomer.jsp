<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addcustomer.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/tb/do/StoreCustomer?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}	
			
</script>


</head>
<body>

<html:form action="/StoreCustomer">

	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<p>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.entercustomerproperties.text" />:<br></span>
	</p>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customer.name.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="name" size="40"
				maxlength="<%="" + org.tb.GlobalConstants.CUSTOMERNAME_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="name" /></span></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customer.shortname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="shortname" size="40"
				maxlength="12" />
			<span style="color:red"><html:errors property="shortname" /></span></td>
		</tr>
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customer.address.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:textarea
				property="address" cols="30" rows="4" /> <span style="color:red"><html:errors
				property="address" /></span></td>
		</tr>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false'); return false" styleId="button"  titleKey="main.general.button.save.alttext.text">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'true'); return false" styleId="button" titleKey="main.general.button.saveandcontinue.alttext.text">
				<bean:message key="main.general.button.saveandcontinue.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button" titleKey="main.general.button.reset.alttext.text">
				<bean:message key="main.general.button.reset.text" />
			</html:submit></td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>

