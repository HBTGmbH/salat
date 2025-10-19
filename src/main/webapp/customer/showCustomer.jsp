<%@page pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<tiles:insert definition="page">
	<tiles:put name="menuactive" direct="true" value="order" />
	<tiles:put name="section" direct="true"><bean:message key="main.general.mainmenu.customers.text"/></tiles:put>
	<tiles:put name="subsection" direct="true"><bean:message key="main.general.mainmenu.customers.text"/></tiles:put>
	<tiles:put name="scripts" direct="true">
		<script type="text/javascript" language="JavaScript">
			function confirmDelete(id) {
				var agree=confirm("<bean:message key="main.general.confirmdelete.text" />");
				if (agree) {
					location.href = "DeleteCustomer?cuId=" + id;
				}
			}

			function refresh(form) {
				form.action = "/do/ShowCustomer?task=refresh";
				form.submit();
			}

			function showWMTT(Trigger,id) {
				wmtt = document.getElementById(id);
				var hint;
				hint = Trigger.getAttribute("hint");
				//if((hint != null) && (hint != "")){
				//wmtt.innerHTML = hint;
				wmtt.style.display = "block";
				//}
			}

			function hideWMTT() {
				wmtt.style.display = "none";
			}
		</script>
	</tiles:put>
	<tiles:put name="content" direct="true">
        <div class="mb-3">
            <html:errors/>
        </div>

        <div class="card">
            <div class="card-body">
                <div class="row g-2 align-items-center">
                    <div class="col-md-8">
                        <html:form action="/ShowCustomer?task=refresh" styleClass="row g-2">
                            <div class="col-auto d-flex align-items-center">
                                <label class="form-label me-2 mb-0"><bean:message key="main.general.filter.text" /></label>
                                <html:text property="filter" styleClass="form-control" size="40" />
                            </div>
                            <div class="col-auto">
                                <html:submit styleClass="btn btn-primary" titleKey="main.general.button.filter.alttext.text">
                                    <bean:message key="main.general.button.filter.text" />
                                </html:submit>
                            </div>
                        </html:form>
                    </div>
                    <div class="col-md-4 text-end">
                        <bean:size id="customersSize" name="customers" />
                        <c:if test="${customersSize>10}">
                            <c:if test="${authorizedUser.manager}">
                                <html:form action="/CreateCustomer">
                                    <html:submit styleClass="btn btn-success" titleKey="main.general.button.createcustomer.alttext.text">
                                        <i class="ti ti-plus"></i>
                                        <bean:message key="main.general.button.createcustomer.text" />
                                    </html:submit>
                                </html:form>
                            </c:if>
                        </c:if>
                    </div>
                </div>
            </div>
            <div class="table-responsive">
                <table class="table table-hover table-striped card-table">
                    <thead>
                    <tr>
                        <th>Info</th>
                        <th><bean:message key="main.customer.shortname.text" /></th>
                        <th><bean:message key="main.customer.name.text" /></th>
                        <th><bean:message key="main.customer.address.text" /></th>
                        <c:if test="${authorizedUser.manager}">
                            <th>Actions</th>
                        </c:if>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="customer" items="${customers}" varStatus="statusID">
                        <tr>
                            <td><c:out value="${customer.id}" /></td>
                            <td><c:out value="${customer.shortName}" /></td>
                            <td><c:out value="${customer.name}" /></td>
                            <td><c:out value="${customer.address}" /></td>

                            <c:if test="${authorizedUser.manager}">
                                <td>
                                    <div class="btn-list flex-nowrap">
                                        <a href="EditCustomer?cuId=${customer.id}" class="btn"> Edit </a>
                                        <a onclick="confirmDelete(${customer.id})" class="btn"> Delete </a>
                                    </div>
                                </td>
                            </c:if>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
            <c:if test="${authorizedUser.manager}">
                <div class="card-footer">
                    <button onclick="location.href='CreateCustomer'" class="btn btn-success">
                        <i class="ti ti-plus"></i>
                        <bean:message key="main.general.button.createcustomer.text" />
                    </button>
                </div>
            </c:if>
        </div>
	</tiles:put>
</tiles:insert>
