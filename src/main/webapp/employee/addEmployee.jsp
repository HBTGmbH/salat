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
    <tiles:put name="menuactive" direct="true" value="employee" />
    <tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.employees.text"/></tiles:put>
    <tiles:put name="subsection" direct="true"><bean:message key="main.general.addemployee.text"/></tiles:put>
    <tiles:put name="scripts" direct="true">
        <script type="text/javascript" language="JavaScript">
            function setStoreAction(form, actionVal, addMore) {
                form.action = "/do/StoreEmployee?task=" + actionVal + "&continue=" + addMore;
                form.submit();
            }
        </script>
    </tiles:put>
    <tiles:put name="content" direct="true">
        <html:form action="/StoreEmployee">
        <html:errors prefix="form.errors.prefix" suffix="form.errors.suffix" header="form.errors.header" footer="form.errors.footer" />
        <table border="0" cellspacing="0" cellpadding="2"
               class="center backgroundcolor">
		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.firstname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="firstname" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.EMPLOYEE_FIRSTNAME_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="firstname" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.lastname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="lastname" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.EMPLOYEE_LASTNAME_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="lastname" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.sign.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="sign" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.EMPLOYEE_SIGN_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="sign" /></span></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.loginname.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:text
				property="loginname" size="30"
				maxlength="<%=String.valueOf(org.tb.common.GlobalConstants.EMPLOYEE_LOGINNAME_MAX_LENGTH) %>" />
			<span style="color:red"><html:errors property="loginname" /></span></td>
		</tr>

		

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.status.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="status" >
				<html:option value="ma">
					<bean:message key="main.employee.status.ma" />
				</html:option>
				<!-- 
				<html:option value="av">
					<bean:message key="main.employee.status.av" />
				</html:option>
				<html:option value="pl">
					<bean:message key="main.employee.status.pl" />
				</html:option>
				-->
				<html:option value="bo">
					<bean:message key="main.employee.status.bo" />
				</html:option>
				<html:option value="bl">
					<bean:message key="main.employee.status.bl" />
				</html:option>
				<html:option value="pv">
					<bean:message key="main.employee.status.pv" />
				</html:option>
				<html:option value="restricted">
					<bean:message key="main.employee.status.restricted" />
				</html:option>
				<html:option value="adm">
					<bean:message key="main.employee.status.adm" />
				</html:option>
			</html:select></td>
		</tr>

		<tr>
			<td align="left" class="noBborderStyle"><b><bean:message
				key="main.employee.gender.text" /></b></td>
			<td align="left" class="noBborderStyle"><html:select
				property="gender" >
				<html:option value="m">
					<bean:message key="main.general.employee.gender.male" />
				</html:option>
				<html:option value="f">
					<bean:message key="main.general.employee.gender.female" />
				</html:option>
			</html:select></td>
		</tr>
	</table>
	<br>
	<table class="center">
		<tr>
			<td class="noBborderStyle"><html:submit
				onclick="setStoreAction(this.form, 'save', 'false');return false" styleId="button"  titleKey="main.general.button.save.alttext.text">
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
    </tiles:put>
</tiles:insert>
