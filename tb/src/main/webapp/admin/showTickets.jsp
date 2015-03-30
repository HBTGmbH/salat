<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html-el"%>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html:html>

<head>
	<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
	<title>
		<bean:message key="main.general.application.title" /> -
		<bean:message key="main.general.mainmenu.tickets.text" />
	</title>
	
	<link rel="stylesheet" type="text/css" href="/tb/tb.css" />


	<script	type="text/javascript" language="JavaScript" src="/tb/CalendarPopup.js"></script>
	<script type="text/javascript" language="JavaScript">document.write(getCalendarStyles());</script> 

	<script type="text/javascript" language="JavaScript">
	
		function calenderPopup(cal) {
            cal.setMonthNames(<bean:message key="main.date.popup.monthnames" />);
            cal.setDayHeaders(<bean:message key="main.date.popup.dayheaders" />);
            cal.setWeekStartDay(<bean:message key="main.date.popup.weekstartday" />);
            cal.setTodayText("<bean:message key="main.date.popup.today" />");
        }
        function calenderPopupFrom(index, id) {
            var cal = new CalendarPopup();
            selectedTicketIndex = index;
            selectedTicketID = id;
            calenderPopup(cal);
            cal.select(document.forms[index].fromDate,'from','yyyy-MM-dd');
        }
        function calenderPopupUntil(index, id) {
            var cal = new CalendarPopup();
            selectedTicketIndex = index;
            selectedTicketID = id;
            calenderPopup(cal);
            cal.select(document.forms[index].untilDate,'until','yyyy-MM-dd');
        }	

		function saveTicket(form, ticketId) {
			form.action = "/tb/do/ShowTickets?save=" + ticketId;
			form.submit();
		}
		
		function setSuborderToTicket(form, ticketId) {
			form.action = "/tb/do/ShowTickets?setSuborder=" + ticketId;
			form.submit();
		}
		
		//action fired after calender has been clicked (from CalendarPopup.js)
		function afterCalenderClick() {
			document.forms[selectedTicketIndex].action = "/tb/do/ShowTickets?setDate=" + selectedTicketID;
			document.forms[selectedTicketIndex].submit();	
		}	
		
		function setUpdateTickets(form) {
			form.action = "/tb/do/ShowTickets";
			form.submit();
		
		}
		
		function refresh(form) {	
			form.action = "/tb/do/ShowTickets?task=refresh";
			form.submit();
		}
						
	 	function showWMTT(Trigger,id) {
			wmtt = document.getElementById(id);
	    	var hint;
			hint = Trigger.getAttribute("hint");
	    	wmtt.style.display = "block";
		}
	
		function hideWMTT() {
			wmtt.style.display = "none";
		}
		
	</script>

</head>


<body>
	<jsp:include flush="true" page="/menu.jsp">
		<jsp:param name="title" value="Menu" />
	</jsp:include>
	
	<br>
	
	<span style="font-size: 14pt; font-weight: bold;">
		<br>
		<bean:message key="main.general.mainmenu.tickets.text" />	
		<br>	
	</span>
	
	<br>

	<span style="color: red"><html:errors footer="<br>" /></span>

	<html:form action="/ShowTickets?task=refresh">
	
	<table class="center backgroundcolor">
		<colgroup>
			<col align="left" width="185" />
			<col align="left" width="750" />
		</colgroup>
		
		<!-- select order -->
		<tr>
			<td align="left" class="noBborderStyle">
				<b><bean:message key="main.employeeorder.customerorder.text" />:</b>
			</td>
			<td align="left" class="noBborderStyle">
				<html:select property="orderId" onchange="refresh(this.form)" value="${currentOrderId}">
					<html:option value="-1">
						<bean:message key="main.general.allorders.text" />
					</html:option>
					<html:options collection="customerOrders" labelProperty="signAndDescription" property="id" />
				</html:select>	
			</td>
		</tr>
		<!-- select suborder -->
		<tr>
	        <td align="left" class="noBborderStyle">
	        	<b><bean:message key="main.employeeorder.suborder.text" />:</b>
	        </td>
			<td align="left" class="noBborderStyle">
	           <html:select property="suborderId" onchange="refresh(this.form)" value="${currentSuborderId}">
					<html:option value="-1">
						<bean:message key="main.general.allsuborders.text" />
					</html:option>
					<c:forEach var="suborder" items="${suborders}">
						<html:option value="${suborder.id}">
							${suborder.signAndDescription}
							<c:if test="${!suborder.currentlyValid}">
								&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
							</c:if>
						</html:option>
					</c:forEach>
				</html:select>
				<span style="font-size: 0.6em">
					<bean:message key="main.general.select.expired.text" />
				</span>
			</td>		
		</tr>
	</table>
	</html:form>
	


<table class="center backgroundcolor">
	<tr>
	<!-- TODO: message values kopieren??? -->
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.ordernumber.text' />" colspan="1">
			<b><bean:message key="main.employeeorder.customerorder.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.subordernumber.text' />">
			<b><bean:message key="main.employeeorder.suborder.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.tickets.jiraticketkey.text' />"	colspan="1">
			<b><bean:message key="main.tickets.jiraticketkey.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.validfrom.text' />">
			<b><bean:message key="main.employeeorder.validfrom.text" /></b>
		</th>
		<th align="left" title="<bean:message key='main.headlinedescription.employeeorders.validuntil.text' />">
			<b><bean:message key="main.employeeorder.validuntil.text" /></b>
		</th>
		<c:if test="${employeeAuthorized}">
			<th align="left" title="<bean:message
				key="main.headlinedescription.tickets.saveticket.text" />"><b><bean:message
				key="main.tickets.saveticket.text" /></b></th>
		</c:if>
	</tr>
	
	<c:forEach var="ticket" items="${tickets}" varStatus="statusID">
		<html:form action="/ShowTickets">
		
		 
		<c:choose>
			<c:when test="${statusID.count % 2 == 0}">
				<tr>
				<c:set var="bgcolor" scope="session" value="#c1d5eb"/>
			</c:when>
			<c:otherwise>
				<tr>
				<c:set var="bgcolor" scope="session" value="transparent"/>
			</c:otherwise>
		</c:choose>
		


		<td bgcolor="${bgcolor}" ><c:out value="${currentOrderDescr}" /></td>
		
		<td align="left" bgcolor="${bgcolor}">
			<c:choose>
			<c:when test="${employeeAuthorized == true}">
				<html:select property="newSuborderId" onchange="setSuborderToTicket(this.form, ${ticket.id})" value="${ticket.pickedSuborderId}">
					<c:forEach var="suborder" items="${suborders}">
						<html:option value="${suborder.id}">
							${suborder.signAndDescription}
							<c:if test="${!suborder.currentlyValid}">
								&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
							</c:if>
						</html:option>
					</c:forEach>
				</html:select>
			</c:when>
			<c:otherwise>
				<c:forEach var="suborder" items="${suborders}">
					<c:if test="${suborder.id == ticket.pickedSuborderId}">
						${suborder.signAndDescription}
						&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(&dagger; ${suborder.formattedUntilDate})
					</c:if>
				</c:forEach>
			</c:otherwise>
			</c:choose>
		</td>
			
			
		<td align=center bgcolor="${bgcolor}"><c:out value="${ticket.jiraTicketKey}" /></td>
		
		<td bgcolor="${bgcolor}">
			<c:choose>
				<c:when test="${employeeAuthorized == true}">
	                <html:text property="fromDate" value="${ticket.pickedFromDate}" 
	                	readonly="false" size="10" maxlength="10" />
					<a href="javascript:calenderPopupFrom(${ticket.index}, ${ticket.id})" 
						name="from" ID="from" style="text-decoration: none;">
						<img src="/tb/images/popupcalendar.gif" width="22" height="22" 
							alt="<bean:message key="main.date.popup.alt.text" />" 
							style="border:0; vertical-align:top">
					</a>
				</c:when>
				<c:otherwise>
					<c:out value="${ticket.fromDate}" />
				</c:otherwise>
			</c:choose>	
		</td>
		
		
		<td bgcolor="${bgcolor}"> 
			<c:choose>
			<c:when test="${employeeAuthorized == true}">
                <html:text property="untilDate" value="${ticket.pickedUntilDate}" 
                	readonly="false" size="10" maxlength="10" />
                <a href="javascript:calenderPopupUntil(${ticket.index}, ${ticket.id})" 
                	name="until" ID="until" style="text-decoration:none;">
                	<img src="/tb/images/popupcalendar.gif" width="22" height="22" 
                		alt="<bean:message key="main.date.popup.alt.text" />"
						style="border:0; vertical-align:top"> 
				</a>
			</c:when>
			<c:otherwise>
				<c:out value="${ticket.untilDate}" />
			</c:otherwise>
			</c:choose>	
		</td>
		
		<c:if test="${employeeAuthorized}">
		<c:choose>
			<c:when test="${((ticket.pickedSuborderId != ticket.suborderId || ticket.pickedUntilDate != ticket.untilDate || ticket.pickedFromDate != ticket.fromDate) && ticket.error == false)}">
				<td align="center" bgcolor="${bgcolor}">
					<button style="background-color:transparent; border:none; cursor: pointer;" onclick="saveTicket(this.form, ${ticket.id})">
						<html:image src="/tb/images/disk.png" alt="Save Changes"/>
					</button>
				</td>
			</c:when>
			<c:otherwise>
				<td align="center" bgcolor="${bgcolor}">
					<button style="background-color:transparent; border:none;" disabled>
						<html:image src="/tb/images/disk_gray.png" alt="Nothing to save"/>
					</button>
				</td> 
			</c:otherwise>
		</c:choose>
		</c:if>
		
		<c:choose>
			<c:when test="${ticket.error}">
				<td  class="noBborderStyle" style="color:red">
					<!-- <bean:message key="form.ticketoverview.error.maerror"/> --> 
					<p>${ticket.errorMessage}</p>
				</td>
			</c:when>
			<c:otherwise>
				<td class="noBborderStyle"></td> 
			</c:otherwise>
		</c:choose>	
		
		
	</html:form>		
	</c:forEach>
</table>



</body>
</html:html>
