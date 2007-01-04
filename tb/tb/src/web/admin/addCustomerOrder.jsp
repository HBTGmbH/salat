<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addcustomerorder.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/tb/do/StoreCustomerorder?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}
		
	function afterCalenderClick() {
	}
					
</script>

</head>
<body>

<html:form action="/StoreCustomerorder">

	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	
	<h2><p><bean:message
		key="main.general.entercustomerorderproperties.text" />:</p></h2>
	
	<br>

	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.customer.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="customerId">
				<html:options collection="customers" labelProperty="name"
					property="id" />
			</html:select></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.sign.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="sign" size="40"
				maxlength="<%="" + org.tb.GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="sign" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.validfrom.text" /></b></td>
			<td align="left" class="noBborderStyle"><!-- JavaScript Stuff for popup calender -->
			<script type="text/javascript" language="JavaScript"
				src="/tb/CalendarPopup.js"></script> <script type="text/javascript"
				language="JavaScript">
                    document.write(getCalendarStyles());
                </script> <script type="text/javascript" language="JavaScript">
                    function calenderPopup(cal) {
                        cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
                        cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
                        cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
                        cal.setTodayText("<bean:message key="main.date.popup.today" />");
                    }
                    function calenderPopupFrom() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        //cal.select(document.forms[0].validFrom,'from','E yyyy-MM-dd');
                        cal.select(document.forms[0].validFrom,'from','yyyy-MM-dd');
                    }
                    function calenderPopupUntil() {
                        var cal = new CalendarPopup();
                        calenderPopup(cal);
                        //cal.select(document.forms[0].validUntil,'until','E yyyy-MM-dd');
                        cal.select(document.forms[0].validUntil,'until','yyyy-MM-dd');
                    }
                </script> <html:text property="validFrom" readonly="false"
				size="10" maxlength="10" /> <a
				href="javascript:calenderPopupFrom()" name="from" ID="from"
				style="text-decoration:none;"> <img
				src="/tb/images/popupcalendar.gif" width="22" height="22"
				alt="<bean:message key="main.date.popup.alt.text" />"
				style="border:0;vertical-align:top"> </a> <span style="color:red"><html:errors
				property="validFrom" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.validuntil.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="validUntil" readonly="false" size="10" maxlength="10" />
			<a href="javascript:calenderPopupUntil()" name="until" ID="until"
				style="text-decoration:none;"> <img
				src="/tb/images/popupcalendar.gif" width="22" height="22"
				alt="<bean:message key="main.date.popup.alt.text" />"
				style="border:0;vertical-align:top"> </a> <span style="color:red"><html:errors
				property="validUntil" /></span></td>
		</tr>
		
		<!-- Bezeichnung -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.description.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:textarea
				property="description" cols="30" rows="4" /> <span style="color:red"><html:errors
				property="description" /></span></td>
		</tr>
		
		<!-- Auftragverantwortlicher bei HBT -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblehbt.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<html:select property="employeeId">
					<html:options collection="employeeswithcontract" labelProperty="name"
						property="id" />
				</html:select>
			</td>
		</tr>
		
		<!-- Verantwortlicher beim Kunden (fachlich) -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblecustomer.tech.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="responsibleCustomerTechnical" size="40"
				maxlength="<%="" + org.tb.GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH %>" />
			<span style="color:red"><html:errors
				property="responsibleCustomerTechnical" /></span></td>
		</tr>

		<!-- Verantwortlicher beim Kunden (vertraglich) -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.responsiblecustomer.contract.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="responsibleCustomerContractually" size="40"
				maxlength="<%="" + org.tb.GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="responsibleCustomerContractually" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.ordercustomer.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="orderCustomer" size="40"
				maxlength="<%="" + org.tb.GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="orderCustomer" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.currency.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="currency" size="10"
				maxlength="<%="" + org.tb.GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="currency" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.hourlyrate.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="hourlyRate" size="10" /> <span style="color:red"><html:errors
				property="hourlyRate" /></span></td>
		</tr>

	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button">
				<bean:message key="main.general.button.saveandcontinue.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button">
				<bean:message key="main.general.button.reset.text" />
			</html:submit></td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>

