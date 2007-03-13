<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> -
<bean:message key="main.general.addsuborder.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/tb/do/StoreSuborder?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}	
			
	function afterCalenderClick() {
	}
	
</script>

</head>
<body>

<html:form action="/StoreSuborder">

	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br>
	<bean:message key="main.general.entersuborderproperties.text" />:<br>
	</span>
	<br>
	<table class="center backgroundcolor">
		
		<!-- select customerorder -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.customerorder.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="customerorderId" value="${currentOrderId}"
				onchange="setStoreAction(this.form,'refreshHourlyRate')">
				<html:options collection="customerorders"
					labelProperty="signAndDescription" property="id" />
			</html:select><span style="color:red"><html:errors property="customerorder" /></span></td>
		</tr>

		<!-- enter suborder sign -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.sign.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="sign" size="40"
				maxlength="<%="" + org.tb.GlobalConstants.SUBORDER_SIGN_MAX_LENGTH %>" />
			<span style="color:red"><html:errors property="sign" /></span></td>
		</tr>
		
		<!-- from date -->
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
				size="12" maxlength="10" /> <a
				href="javascript:calenderPopupFrom()" name="from" ID="from"
				style="text-decoration:none;"> <img
				src="/tb/images/popupcalendar.gif" width="22" height="22"
				alt="<bean:message key="main.date.popup.alt.text" />"
				style="border:0;vertical-align:top"> </a> (<bean:message
				key="main.suborder.customerorder.text" />: <c:out value="${currentOrder.fromDate}" />) <span 
				style="color:red"><html:errors
				property="validFrom" /></span></td>
		</tr>
		
		<!-- until date -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.customerorder.validuntil.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="validUntil" readonly="false" size="12" maxlength="10" />
			<a href="javascript:calenderPopupUntil()" name="until" ID="until"
				style="text-decoration:none;"> <img
				src="/tb/images/popupcalendar.gif" width="22" height="22"
				alt="<bean:message key="main.date.popup.alt.text" />"
				style="border:0;vertical-align:top"> </a> (<bean:message
				key="main.suborder.customerorder.text" />: <c:choose>
						<c:when test="${currentOrder.untilDate == null}">
							<bean:message key="main.general.open.text" />)
						</c:when>
						<c:otherwise>
							<c:out value="${currentOrder.untilDate}" />)
						</c:otherwise>
					</c:choose> <span style="color:red"><html:errors
				property="validUntil" /></span></td>
		</tr>
		
		<!-- description -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.description.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:textarea
				property="description" cols="30" rows="4" /> <span
				style="color:red"><html:errors property="description" /></span></td>
		</tr>

		<!-- short description -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.shortdescription.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="shortdescription" size="20" maxlength="20" /> <span
				style="color:red"><html:errors property="shortdescription" /></span></td>
		</tr>
		
		<!-- invoice -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.invoice.text" />:</b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="invoice" value="${invoice}">
				<html:option value="Y">
					<bean:message key="main.general.suborder.invoice.yes" />
				</html:option>
				<html:option value="N">
					<bean:message key="main.general.suborder.invoice.no" />
				</html:option>
				<html:option value="U">
					<bean:message key="main.general.suborder.invoice.undefined" />
				</html:option>
			</html:select> <span style="color:red"><html:errors property="invoice" /></span></td>

		</tr>

		<!-- hourly rate & currency -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.hourlyrate.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="hourlyRate" size="20" value="${hourlyRate}" /> <html:select property="currency">
					<html:option value="EUR">EUR</html:option>
				</html:select> <span style="color:red"><html:errors property="hourlyRate" /></span></td>
		</tr>
		
		<!-- debithours -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.general.debithours.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="debithours" size="20" />&nbsp;&nbsp;<bean:message
				key="main.general.per.text" />&nbsp;&nbsp;&nbsp;&nbsp;
				<html:radio property="debithoursunit" value="12" disabled="false" /><bean:message
				key="main.general.month.text" /> <html:radio 
				property="debithoursunit" value="1" disabled="false" /><bean:message
				key="main.general.year.text" /> <html:radio 
				property="debithoursunit" value="0" disabled="false" /><bean:message
				key="main.general.totaltime.text" /> <span 
				style="color:red"><html:errors property="debithours" /></span>
			</td>
		</tr>

		<!-- is it a standard suborder? -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.standard.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="standard" /></td>
		</tr>

		<!-- comment necessary? -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.suborder.commentnecessary.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="commentnecessary" /></td>
		</tr>
		
		<!-- hide -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.general.hideinselectboxes.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="hide" /> </td>
		</tr>


	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false"
				styleId="button" titleKey="main.general.button.save.alttext.text">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'true');return false"
				styleId="button"
				titleKey="main.general.button.saveandcontinue.alttext.text">
				<bean:message key="main.general.button.saveandcontinue.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'reset', 'false')"
				styleId="button" titleKey="main.general.button.reset.alttext.text">
				<bean:message key="main.general.button.reset.text" />
			</html:submit></td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>

