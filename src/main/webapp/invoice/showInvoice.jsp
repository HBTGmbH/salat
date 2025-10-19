<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<tiles:insert definition="page">
    <tiles:put name="menuactive" direct="true" value="order" />
    <tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.orders.text"/></tiles:put>
    <tiles:put name="subsection" direct="true"><bean:message key="main.general.mainmenu.invoice.title.text"/></tiles:put>
    <tiles:put name="scripts" direct="true">
        <script type="text/javascript" language="JavaScript">
            function updateOptions(form) {
                form.target = "_self";
                form.action = "/do/ShowInvoice?task=updateOptions";
                form.submit();
            }
            function exportExcel(form) {
                form.target = "_self";
                form.action = "/do/ShowInvoice?task=export";
                form.submit();
            }
            function print(form) {
                form.target = "_blank";
                form.action = "/do/ShowInvoice?task=print";
                form.submit();
            }
            $(document).ready(function() {
                $(".make-select2").select2({
                    dropdownAutoWidth: true,
                    width: 'auto'
                });
            });
        </script>
    </tiles:put>
    <tiles:put name="content" direct="true">
        <html:form action="/ShowInvoice?task=generateMaximumView">
			<table class="center backgroundcolor" style="float: left; margin-right: 20px">
				<!-- dataset options title -->
				<tr>
					<td align="left" class="noBborderStyle">
						<h3 style="text-decoration: underline"><bean:message key="main.invoice.dataset.text" /></h3>
					</td>
					<td align="left" class="noBborderStyle"></td>
				</tr>
				<!-- select order -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.customerorder.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="orderId" styleClass="make-select2" onchange="updateOptions(this.form)">
							<html:option value="">
								<bean:message key="main.invoice.choose.text" />
							</html:option>
							<html:options collection="orders" labelProperty="signAndDescription" property="id" />
						</html:select>
					</td>
				</tr>
				<!-- select suborder -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.monthlyreport.suborder.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="suborderId" onchange="updateOptions(this.form)" styleClass="make-select2">
							<html:option value="">
								<bean:message key="main.general.allsuborders.text" />
							</html:option>
							<html:options collection="suborders" labelProperty="completeOrderSignAndDescription" property="id" />
						</html:select>
						<html:checkbox property="showOnlyValid" onchange="updateOptions(this.form)" styleClass="middle-aligned">
							<span class="middle"><bean:message key="main.general.show.only.valid.text"/></span>
						</html:checkbox>
					</td>
				</tr>

				<!-- select view mode -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.general.timereport.view.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="invoiceview" onchange="updateOptions(this.form)">
							<html:option value="month">
								<bean:message key="main.general.timereport.view.monthly.text" />
							</html:option>
							<html:option value="custom">
								<bean:message key="main.general.timereport.view.custom.text" />
							</html:option>
						</html:select>
					</td>
				</tr>

				<!-- select first date -->
				<tr>
					<td align="left" class="noBborderStyle">
						<c:choose>
							<c:when test="${showInvoiceForm.invoiceview eq 'month'}">
								<b><bean:message key="main.monthlyreport.monthyear.text" />:</b>
							</c:when>
							<c:otherwise>
								<b><bean:message key="main.monthlyreport.daymonthyear.text" />:</b>
							</c:otherwise>
						</c:choose>
					</td>

					<td align="left" class="noBborderStyle">
						<c:if test="${showInvoiceForm.invoiceview eq 'custom'}">
							<html:select property="fromDay" onchange="updateOptions(this.form)">
								<html:options collection="days" property="value" labelProperty="label" />
							</html:select>
						</c:if>
						<html:select property="fromMonth" onchange="updateOptions(this.form)">
							<html:option value="Jan">
								<bean:message key="main.timereport.select.month.jan.text" />
							</html:option>
							<html:option value="Feb">
								<bean:message key="main.timereport.select.month.feb.text" />
							</html:option>
							<html:option value="Mar">
								<bean:message key="main.timereport.select.month.mar.text" />
							</html:option>
							<html:option value="Apr">
								<bean:message key="main.timereport.select.month.apr.text" />
							</html:option>
							<html:option value="May">
								<bean:message key="main.timereport.select.month.may.text" />
							</html:option>
							<html:option value="Jun">
								<bean:message key="main.timereport.select.month.jun.text" />
							</html:option>
							<html:option value="Jul">
								<bean:message key="main.timereport.select.month.jul.text" />
							</html:option>
							<html:option value="Aug">
								<bean:message key="main.timereport.select.month.aug.text" />
							</html:option>
							<html:option value="Sep">
								<bean:message key="main.timereport.select.month.sep.text" />
							</html:option>
							<html:option value="Oct">
								<bean:message key="main.timereport.select.month.oct.text" />
							</html:option>
							<html:option value="Nov">
								<bean:message key="main.timereport.select.month.nov.text" />
							</html:option>
							<html:option value="Dec">
								<bean:message key="main.timereport.select.month.dec.text" />
							</html:option>
						</html:select>
						<html:select property="fromYear" onchange="updateOptions(this.form)">
							<html:options collection="years" property="value" labelProperty="label" />
						</html:select>
					</td>
				</tr>

				<!-- select second date -->
				<c:if test="${showInvoiceForm.invoiceview eq 'custom'}">
					<tr>
						<td align="left" class="noBborderStyle">
							<b><bean:message key="main.monthlyreport.daymonthyear.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:select property="untilDay" onchange="updateOptions(this.form)">
								<html:options collection="days" property="value" labelProperty="label" />
							</html:select>
							<html:select property="untilMonth" onchange="updateOptions(this.form)">
								<html:option value="Jan">
									<bean:message key="main.timereport.select.month.jan.text" />
								</html:option>
								<html:option value="Feb">
									<bean:message key="main.timereport.select.month.feb.text" />
								</html:option>
								<html:option value="Mar">
									<bean:message key="main.timereport.select.month.mar.text" />
								</html:option>
								<html:option value="Apr">
									<bean:message key="main.timereport.select.month.apr.text" />
								</html:option>
								<html:option value="May">
									<bean:message key="main.timereport.select.month.may.text" />
								</html:option>
								<html:option value="Jun">
									<bean:message key="main.timereport.select.month.jun.text" />
								</html:option>
								<html:option value="Jul">
									<bean:message key="main.timereport.select.month.jul.text" />
								</html:option>
								<html:option value="Aug">
									<bean:message key="main.timereport.select.month.aug.text" />
								</html:option>
								<html:option value="Sep">
									<bean:message key="main.timereport.select.month.sep.text" />
								</html:option>
								<html:option value="Oct">
									<bean:message key="main.timereport.select.month.oct.text" />
								</html:option>
								<html:option value="Nov">
									<bean:message key="main.timereport.select.month.nov.text" />
								</html:option>
								<html:option value="Dec">
									<bean:message key="main.timereport.select.month.dec.text" />
								</html:option>
							</html:select>
							<html:select property="untilYear" onchange="updateOptions(this.form)">
								<html:options collection="years" property="value" labelProperty="label" />
							</html:select>
						</td>
					</tr>
				</c:if>

				<!-- show subordercomment -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.suborderdescription.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:select property="suborderdescription" onchange="updateOptions(this.form)">
							<html:option value="longdescription">
								<bean:message key="main.invoice.suborderdescription.long.text" />
							</html:option>
							<html:option value="shortdescription">
								<bean:message key="main.invoice.suborderdescription.short.text" />
							</html:option>
						</html:select>
					</td>
				</tr>
				<!-- show customer orderdescription -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.customersign.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="customeridbox" onchange="updateOptions(this.form)" />
					</td>
				</tr>
				<!-- show invoice -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.invoice.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="invoicebox" onchange="updateOptions(this.form)" />
					</td>
				</tr>
				<!-- show fixed price offers -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.fixedprice.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="fixedpricebox" onchange="updateOptions(this.form)" />
					</td>
				</tr>
				<tr>
					<td class="noBborderStyle" align="left">
						<html:submit styleId="button" titleKey="main.invoice.button.createmaximumview.alttext.text">
							<bean:message key="main.invoice.button.createmaximumview.text" />
						</html:submit>
					</td>
					<td class="noBborderStyle">
						&nbsp;
					</td>
				</tr>
			</table>
			<table class="center backgroundcolor">
				<!-- show/hide options title -->
				<tr>
					<td align="left" class="noBborderStyle">
						<h3 style="text-decoration: underline"><bean:message key="main.invoice.showhide.text" /></h3>
					</td>
					<td align="left" class="noBborderStyle"></td>
				</tr>
				<!-- show timereports -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.timereports.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="timereportsbox" onchange="updateOptions(this.form)" />
					</td>
				</tr>
				<c:if test="${showInvoiceForm.timereportsbox}">
					<!-- show timereport description -->
					<tr>
						<td align="left" class="noBborderStyle" style="padding-left: 20px">
							<b><bean:message key="main.invoice.timreportdescription.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:checkbox property="timereportdescriptionbox" onchange="updateOptions(this.form)" />
						</td>
					</tr>

					<!-- show employee signs -->
					<tr style="margin-left: 20px">
						<td align="left" class="noBborderStyle" style="padding-left: 20px">
							<b><bean:message key="main.invoice.employeesign.text" />:</b>
						</td>
						<td align="left" class="noBborderStyle">
							<html:checkbox property="employeesignbox" onchange="updateOptions(this.form)" />
						</td>
					</tr>
				</c:if>

				<!-- show targethours -->
				<tr>
					<td align="left" class="noBborderStyle">
						<b><bean:message key="main.invoice.targethours.text" />:</b>
					</td>
					<td align="left" class="noBborderStyle">
						<html:checkbox property="targethoursbox" onchange="updateOptions(this.form)" />
					</td>
				</tr>
			</table>

			<c:if test="${invoiceData != null}">
				<table style="clear: both">
					<tr>
						<td class="noBborderStyle" align="left">
							<i>Hinweis: Bearbeiten der Anzeigeoptionen setzt die Änderung der Adresse zurück</i>
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:text size="30" property="titleinvoiceattachment" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:text size="30" property="customername" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:textarea style="width:100%" property="customeraddress" rows="4" />
						</td>
					</tr>
					<tr>
						<td class="noBborderStyle" align="left">
							<html:submit
								onclick="print(this.form)"
								styleId="button"
								titleKey="main.invoice.button.createmaximumview.alttext.text">
								<bean:message key="main.general.button.printpreview.text" />
							</html:submit>
							<html:submit onclick="exportExcel(this.form)" styleId="button"
								titleKey="main.invoice.button.excel.new.text">
								<bean:message key="main.general.button.excelexport.new.text" />
							</html:submit>
							<select name="invoice-settings">
								<option value="HBT">HBT</option>
								<option value="NestorIT">NestorIT</option>
							</select>
						</td>
					</tr>
				</table>
				<table>
					<tr>
						<th></th>
						<th>
							<html:text property="titlesubordertext" />
						</th>
						<c:if test="${showInvoiceForm.timereportsbox}">
							<th>
								<html:text property="titledatetext" />
							</th>
							<c:if test="${showInvoiceForm.employeesignbox && showInvoiceForm.timereportsbox}">
								<th>
									<html:text property="titleemployeesigntext" />
								</th>
							</c:if>
							<c:if test="${showInvoiceForm.timereportdescriptionbox}">
								<th>
									<html:text property="titledescriptiontext" />
								</th>
							</c:if>
						</c:if>
						<c:if test="${showInvoiceForm.targethoursbox}">
							<th>
								<html:text property="titletargethourstext" />
							</th>
						</c:if>
						<th>
							<html:text property="titleactualdurationtext" />
						</th>
						<th>
							<html:text property="titleactualhourstext" />
						</th>
					</tr>
					<c:forEach var="invoiceSuborder" items="${invoiceData.suborders}">
						<tr>
							<td class="noBborderStyle">
								<html:multibox property="suborderIdArray" value="${invoiceSuborder.id}" />
							</td>
							<td>
								<c:out value="${invoiceSuborder.orderDescription}"></c:out>
							</td>
							<%-- Empty cell for timreport dates --%>
							<c:if test="${showInvoiceForm.timereportsbox}">
								<td></td>
								<%-- Empty cell if employee sign should be displayed --%>
								<c:if test="${showInvoiceForm.employeesignbox}">
									<td></td>
								</c:if>
								<%-- Empty cell if task description should be dsisplayed --%>
								<c:if test="${showInvoiceForm.timereportdescriptionbox}">
									<td></td>
								</c:if>
							</c:if>
							<c:if test="${showInvoiceForm.targethoursbox}">
								<td style="text-align: right;">
									<java8:formatDuration value="${invoiceSuborder.budget}" />
								</td>
							</c:if>
							<td style="text-align: right;">
								<java8:formatDuration value="${invoiceSuborder.totalDuration}" />
							</td>
							<td style="text-align: right;">
								<fmt:formatNumber value="${invoiceSuborder.totalHours}" minFractionDigits="2" maxFractionDigits="2" />
							</td>
						</tr>
						<c:forEach var="invoiceTimereport" items="${invoiceSuborder.timereports}">
							<tr>
								<%-- Empty cell for suborderprintcheckbox --%>
								<td class="noBborderStyle"></td>
								<td align="right" class="noBborderStyle">
									<html:multibox property="timereportIdArray" value="${invoiceTimereport.id}" />
								</td>
								<td>
									<java8:formatLocalDate value="${invoiceTimereport.referenceDay}" pattern="dd.MM.yyyy" />
								</td>

								<c:if test="${showInvoiceForm.employeesignbox}">
									<td>
										<c:out value="${invoiceTimereport.employeeName}"></c:out>
									</td>
								</c:if>
								<c:if test="${showInvoiceForm.timereportdescriptionbox}">
									<td>
										<c:out value="${invoiceTimereport.taskDescription}"></c:out>
									</td>
								</c:if>
								<c:if test="${showInvoiceForm.targethoursbox}">
									<td></td>
								</c:if>
								<td style="text-align: right">
									<java8:formatDuration value="${invoiceTimereport.duration}" />
								</td>
								<td style="text-align: right">
									<fmt:formatNumber value="${invoiceTimereport.hours}" minFractionDigits="2" maxFractionDigits="2" />
								</td>
							</tr>
						</c:forEach>
					</c:forEach>
					<tr>
						<td class="noBborderStyle">
							&nbsp;
						</td>
						<td class="noBborderStyle">
							&nbsp;
						</td>
						<c:if test="${showInvoiceForm.timereportsbox}">
							<td class="noBborderStyle"></td>
							<c:if test="${showInvoiceForm.employeesignbox}">
								<td class="noBborderStyle"></td>
							</c:if>
							<c:if test="${showInvoiceForm.timereportdescriptionbox}">
								<td class="noBborderStyle">
								<c:if test="${not showInvoiceForm.targethoursbox}">
									<bean:message key="main.invoice.overall.text" />
								</c:if>
								</td>
							</c:if>
						</c:if>
						<c:if test="${showInvoiceForm.targethoursbox}">
							<td class="noBborderStyle"><bean:message key="main.invoice.overall.text" /></td>
						</c:if>
						<th style="text-align: right">
							<java8:formatDuration value="${invoiceData.totalDurationVisible}" /><br/>
						</th>
						<th style="text-align: right;">
							<fmt:formatNumber value="${invoiceData.totalHoursVisible}" minFractionDigits="2" maxFractionDigits="2" /><br/>
						</th>
					</tr>
					<tr>
						<td class="noBborderStyle">
							&nbsp;
						</td>
						<td class="noBborderStyle">
							&nbsp;
						</td>
						<c:if test="${showInvoiceForm.timereportsbox}">
							<td class="noBborderStyle">
								&nbsp;
							</td>
						</c:if>
						<c:if test="${showInvoiceForm.employeesignbox && showInvoiceForm.timereportsbox}">
							<td class="noBborderStyle"></td>
						</c:if>
						<c:if test="${showInvoiceForm.targethoursbox}">
							<td class="noBborderStyle"></td>
						</c:if>
						<td class="noBborderStyle" style="text-align:right;">

						</td>
						<td class="noBborderStyle" style="text-align: right;">
							<c:if test="${invoiceData.totalDurationVisible != invoiceData.totalDuration}">
								(<java8:formatDuration value="${invoiceData.totalDuration}" />)
							</c:if>
						</td>
						<td class="noBborderStyle" style="text-align: right;">
							<c:if test="${invoiceData.totalHoursVisible != invoiceData.totalHours}">
								(<fmt:formatNumber value="${invoiceData.totalHours}" minFractionDigits="2" maxFractionDigits="2" />)
							</c:if>
						</td>
					</tr>
				</table>
			</c:if>
		</html:form>
    </tiles:put>
</tiles:insert>
