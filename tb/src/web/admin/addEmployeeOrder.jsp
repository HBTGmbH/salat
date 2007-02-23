<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
     "http://www.w3.org/TR/html4/loose.dtd">

<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addemployeeorder.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/tb/do/StoreEmployeeorder?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}
	
	function afterCalenderClick() {
	}
			
</script>

</head>
<body>

<html:form action="/StoreEmployeeorder">
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message
		key="main.general.enteremployeeorderproperties.text" />:<br></span>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" colspan="2" class="noBborderStyle">
				<span style="color:red"><html:errors property="overleap" /></span>
			</td>
		</tr>
		<tr>
			<td align="left" colspan="2" class="noBborderStyle"><c:choose>
				<c:when test="${newemployeeorder}">
					<i><bean:message key="main.employeeorder.new.text" /></i>
				</c:when>
				<c:otherwise>
					<i><bean:message key="main.employeeorder.modify.text" /></i>
				</c:otherwise>
			</c:choose></td>
		</tr>
		
		<!-- select employee contract -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.employee.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="employeeContractId">
				<c:forEach var="employeecontract" items="${employeecontracts}" >
					<c:if test="${employeecontract.employee.sign != 'adm' || loginEmployee.sign == 'adm'}">
						<html:option value="${employeecontract.id}">
							<c:out value="${employeecontract.employee.sign}" />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<c:out value="${employeecontract.timeString}" />)
						</html:option>
					</c:if>							
				</c:forEach>
			</html:select> </td>
		</tr>

		<!-- Auftrag -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.customerorder.text" /></b></td>

			<td align="left" class="noBborderStyle"><html:select
				property="orderId"
				onchange="setStoreAction(this.form, 'refreshSuborders')">
				<html:options collection="orderswithsuborders" labelProperty="signAndDescription"
					property="id" />
			</html:select> <c:out value="${selectedcustomerorder.description}"></c:out> <span
				style="color:red"><html:errors property="orderId" /></span></td>
		</tr>
		
		<!-- Unterauftrag -->
		<tr>
		<td align="left" class="noBborderStyle"><b><bean:message
			key="main.employeeorder.suborder.text" /></b></td>
		<td align="left" class="noBborderStyle"><html:select
			property="suborderId" styleClass="mandatory"
			onchange="setStoreAction(this.form, 'refreshSuborderDescription')">
			<html:options collection="suborders" labelProperty="signAndDescription"
				property="id" />
		</html:select> <c:if test="${selectedsuborder!=null}">
			<c:out value="${selectedsuborder.description}"></c:out>
		</c:if> <html:hidden property="suborderId" /> <span style="color:red"><html:errors
			property="suborderId" /></span></td>
		</tr>
		
		<!-- 
		<tr>
            <td align="left" class="noBborderStyle">
                <b><bean:message key="main.employeeorder.sign.text" /></b>
            </td>
     		<td align="left" class="noBborderStyle">
           		     <html:text property="sign" size="30" maxlength="<%="" + org.tb.GlobalConstants.EMPLOYEEORDER_SIGN_MAX_LENGTH %>"/>              
           		 	<span style="color:red"><html:errors property="sign"/></span>
          	</td>
        </tr>  
        -->

		<!-- Gültig ab -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.validfrom.text" /></b></td>
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

		<!-- Gültig bis -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.validuntil.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="validUntil" readonly="false" size="10" maxlength="10" />
			<a href="javascript:calenderPopupUntil()" name="until" ID="until"
				style="text-decoration:none;"> <img
				src="/tb/images/popupcalendar.gif" width="22" height="22"
				alt="<bean:message key="main.date.popup.alt.text" />"
				style="border:0;vertical-align:top"> </a> <span style="color:red"><html:errors
				property="validUntil" /></span></td>
		</tr>

		<!-- Dauerauftrag -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.standingorder.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="standingorder" /> <span style="color:red"><html:errors
				property="standingorder" /></span></td>
		</tr>

		<!-- Sollstunden -->
		<c:if test="${loginEmployee.status == 'adm' || (!(selectedcustomerorder.sign eq 'URLAUB' || selectedcustomerorder.sign eq 'KRANK'))}">
			<tr>
				<td align="left" class="noBborderStyle"><b><bean:message
					key="main.employeeorder.debithours.text" /></b></td>
				<td align="left" class="noBborderStyle"><html:text
					property="debithours" size="20" /> <span style="color:red"><html:errors
					property="debithours" /></span></td>
			</tr>
		</c:if>
		<!--  
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.status.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="status" size="30" />
			<span style="color:red"><html:errors property="status" /></span></td>
		</tr>
		-->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeeorder.statusreport.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="statusreport" /> <span style="color:red"><html:errors
				property="statusreport" /></span></td>
		</tr>

	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button" titleKey="main.general.button.save.alttext.text">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<c:if test="${newemployeeorder}">
				<td class="noBborderStyle"><html:submit
					onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button" titleKey="main.general.button.saveandcontinue.alttext.text">
					<bean:message key="main.general.button.saveandcontinue.text" />
				</html:submit></td>
			</c:if>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'reset', 'false')" styleId="button" titleKey="main.general.button.reset.alttext.text">
				<bean:message key="main.general.button.reset.text" />
			</html:submit></td>
		</tr>
	</table>
	<html:hidden property="id" />
</html:form>
</body>

