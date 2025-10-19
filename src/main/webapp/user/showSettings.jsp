<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="jakarta.tags.functions" prefix="fn"%>
<%@taglib uri="jakarta.tags.fmt" prefix="fmt"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<tiles:insert definition="page">
    <tiles:put name="menuactive" direct="true" value="user" />
    <tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.settings.title.text"/></tiles:put>
    <tiles:put name="subsection" direct="true"><bean:message key="main.general.overview.text"/></tiles:put>
    <tiles:put name="content" direct="true">
        <!-- settings content goes here; currently empty -->
    </tiles:put>
</tiles:insert>
