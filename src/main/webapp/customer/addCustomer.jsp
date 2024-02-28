<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/tree" prefix="myjsp" %>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<tiles:insert definition="page">
	<tiles:put name="menuactive" direct="true" value="order" />
	<tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.customers.text"/></tiles:put>
	<tiles:put name="subsection" direct="true"><bean:message key="main.general.addcustomer.text"/></tiles:put>
	<tiles:put name="scripts" direct="true">
		<script type="text/javascript" language="JavaScript">
			function setStoreAction(form, actionVal, addMore) {
				form.action = "/do/StoreCustomer?task=" + actionVal + "&continue=" + addMore;
				form.submit();
			}
		</script>
	</tiles:put>
	<tiles:put name="content" direct="true">
		<html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />
        <html:form action="/StoreCustomer">
			<table border="0" cellspacing="0" cellpadding="2"
				   class="center backgroundcolor">
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
							key="main.customer.name.text" /></b></td>
					<td align="left" class="noBborderStyle"><html:text
							property="name" size="40"
							maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.CUSTOMERNAME_MAX_LENGTH) %>" />
					</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
							key="main.customer.shortname.text" /></b></td>
					<td align="left" class="noBborderStyle"><html:text
							property="shortname" size="40"
							maxlength="12" />
					</td>
				</tr>
				<tr>
					<td align="left" class="noBborderStyle"><b><bean:message
							key="main.customer.address.text" /></b></td>
					<td align="left" class="noBborderStyle"><html:textarea
							property="address" cols="30" rows="4" />
					</td>
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
	</tiles:put>
</tiles:insert>
