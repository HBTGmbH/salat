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
<title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.addemployeecontract.text" /></title>
<link rel="stylesheet" type="text/css" href="/tb/tb.css" />

<script type="text/javascript" language="JavaScript">
	
	function setStoreAction(form, actionVal, addMore) {	
 		form.action = "/tb/do/StoreEmployeecontract?task=" + actionVal + "&continue=" + addMore;
		form.submit();
	}
		
	function afterCalenderClick() {
	}
		
</script>

</head>
<body>

<html:form action="/StoreEmployeecontract">

	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	<br>
	<span style="font-size:14pt;font-weight:bold;"><br><bean:message
		key="main.general.enteremployeecontractproperties.text" />:<br></span>
	<br>
	<table border="0" cellspacing="0" cellpadding="2"
		class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.employee.text" /></b></td>
			<td align="left" class="noBborderStyle">
				<c:choose>
					<c:when test="${employeeContractContext eq 'create'}">
						<html:select
							property="employee">
						<html:options collection="employees" labelProperty="name"
							property="id" />
						</html:select>  <span style="color:red"><html:errors
							property="employeename" /></span>
					</c:when>
					<c:otherwise>
						<b><c:out value="${currentEmployee}" /></b>
					</c:otherwise>
				</c:choose>
			</td>
		</tr> 
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.taskdescription.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:textarea
				property="taskdescription" cols="40" rows="6" /> <span
				style="color:red"><html:errors property="taskdescription" /></span>
			</td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.validfrom.text" /></b></td>
			<td align="left" class="noBborderStyle">
		
			<!-- JavaScript Stuff for popup calender -->
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
				key="main.employeecontract.validuntil.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="validUntil" readonly="false" size="10" maxlength="10" />
			<a href="javascript:calenderPopupUntil()" name="until" ID="until"
				style="text-decoration:none;"> <img
				src="/tb/images/popupcalendar.gif" width="22" height="22"
				alt="<bean:message key="main.date.popup.alt.text" />"
				style="border:0;vertical-align:top"> </a> <span style="color:red"><html:errors
				property="validUntil" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.freelancer.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="freelancer" /> <span style="color:red"><html:errors
				property="freelancer" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.dailyworkingtime.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="dailyworkingtime" size="10" /> <span style="color:red"><html:errors
				property="dailyworkingtime" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.yearlyvacation.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="yearlyvacation" size="10" /> <span style="color:red"><html:errors
				property="yearlyvacation" /></span></td>
		</tr>
		
		<tr>
			<c:choose>
				<c:when test="${employeeContractContext eq 'create'}">
					<td align="left" class="noBborderStyle"><b><bean:message
						key="main.employeecontract.initialovertime.text" /></b></td>
					<td align="left" class="noBborderStyle"><html:text
						property="initialOvertime" size="10" /> <span style="color:red"><html:errors
						property="initialOvertime" /></span></td>
				</c:when>
				<c:otherwise>
					<td align="left" class="noBborderStyle"></td>	
				</c:otherwise>
			</c:choose>
		</tr>
		
		<!-- hide -->
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employeecontract.hide.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:checkbox
				property="hide" /> </td>
		</tr>

	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button" titleKey="main.general.button.save.alttext.text">
				<bean:message key="main.general.button.save.text" />
			</html:submit></td>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'true');return false" styleId="button" titleKey="main.general.button.saveandcontinue.alttext.text">
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

	<!-- overtime table -->
	 
<html:form action="/StoreEmployeecontract?task=storeOvertime">			
	<c:if test="${employeeContractContext eq 'edit'}">
	<br>
	<br>
	<b><bean:message key="main.employeecontract.overtime.headline.text" /></b>
	<br>
	<br>
		<table class="center backgroundcolor">
			<tr>
				<td class="noBborderStyle" align="right">
					<b><bean:message key="main.employeecontract.overtime.total.text" />:</b>
				</td>
				<th>
					<c:out value="${totalovertime}" />
				</th>
			</tr>
			<tr>
				<th>
					<b><bean:message
						key="main.employeecontract.overtime.date.text" /></b>
				</th>
				<th>
					<b><bean:message
						key="main.employeecontract.overtime.duration.text" /></b>
				</th>
				<th>
					<b><bean:message
						key="main.employeecontract.overtime.comment.text" /></b>
				</th>
			</tr>
			<c:forEach var="overtime" items="${overtimes}" varStatus="statusID">
				<c:choose>
					<c:when test="${statusID.count%2==0}">
						<tr class="primarycolor">
					</c:when>
					<c:otherwise>
						<tr class="secondarycolor">
					</c:otherwise>
				</c:choose>
					<td align="center"> 
						<c:out value="${overtime.createdString}" />
					</td>
					<td align="center"> 
						<c:out value="${overtime.time}" />
					</td>
					<td align="left">
						<c:out value="${overtime.comment}" />
					</td>
				</tr>	
			</c:forEach>
			<tr>
				<td align="center">
					<c:out value="${dateString}" />
				</td>
				<td>
					<html:text property="newOvertime" size="10" /> 
				</td>					
				<td>
					<html:text property="newOvertimeComment" size="64" />
				</td>
				<td class="noBborderStyle">
					<html:submit styleId="button" styleClass="hiddencontent" titleKey="main.general.button.save.alttext.text">
						<bean:message key="main.general.button.save.text" />						
					</html:submit>
				</td>
			</tr>
			<tr>
				<td class="noBborderStyle" align="right">
					<b><bean:message key="main.employeecontract.overtime.total.text" />:</b>
				</td>
				<th>
					<c:out value="${totalovertime}" />
				</th>
			</tr>
			
			<!-- error messages -->
			
			<tr>
				<td class="noBborderStyle" colspan="4">
					<span style="color:red"><html:errors property="newOvertime" /></span>
				</td>
			</tr>
			<tr>
				<td class="noBborderStyle" colspan="4">
					<span style="color:red"><html:errors property="newOvertimeComment" /></span>
				</td>
			</tr>		
		</table>
	</c:if>
</html:form>

</body>

