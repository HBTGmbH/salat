<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<html:html>
<head>
    <title><bean:message key="chicoree.product.title" /></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="<c:url value="/webjars/bootstrap/css/bootstrap.min.css" />"
          id="bootstrap-css"></link>
    <link rel="stylesheet" href="<c:url value="/webjars/bootstrap-icons/font/bootstrap-icons.min.css"/>">
</head>
<body>
<div class="container">
    <html:messages id="msg" message="true">
        <div class="alert alert-danger" role="alert"><bean:write name="msg" /></div>
    </html:messages>
    <div class="card">
        <div class="card-header text-center h5">
            <i class="bi bi-alarm"></i>
            <span><bean:message key="chicoree.title.timereport" /></span>
        </div>
        <div class="card-body bg-light">
            <html:form action="/chicoree/StoreTimereport">

                <!-- Date input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="date"><bean:message key="main.timereport.referenceday.text" /></label>
                    <html:text property="date" styleId="date"
                           onchange="refreshTimereportFormFields(this.form, 'date-selected')"
                           styleClass="form-control" />
                    <html:messages id="errmsg" property="date">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Order input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="orderId"><bean:message key="main.timereport.customerorder.text" /></label>
                    <html:select property="orderId"
                                 styleClass="form-select"
                                 styleId="orderId"
                                 onchange="refreshTimereportFormFields(this.form, 'date-selected')">
                        <html:option value="" disabled="true"><bean:message key="chicoree.placeholder.order" /></html:option>
                        <html:options collection="orderOptions" labelProperty="label" property="value" />
                    </html:select>
                    <html:messages id="errmsg" property="orderId">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Suborder input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="suborderId"><bean:message key="main.timereport.suborder.text" /></label>
                    <html:select property="suborderId"
                                 styleClass="form-select"
                                 styleId="suborderId">
                        <html:options collection="suborderOptions" labelProperty="label" property="value" />
                    </html:select>
                    <html:messages id="errmsg" property="suborderId">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Duration input -->
                <div class="row g-3 mb-4">
                    <div class="col">
                        <label class="form-label" for="hours"><bean:message key="main.timereport.hours.text" /></label>
                        <html:text styleId="hours" property="hours" styleClass="text-center form-control" />
                        <html:messages id="errmsg" property="hours">
                            <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                        </html:messages>
                    </div>
                    <div class="col">
                        <label class="form-label" for="minutes"><bean:message key="main.timereport.minutes.text" /></label>
                        <html:text styleId="minutes" property="minutes" styleClass="text-center form-control" />
                        <html:messages id="errmsg" property="minutes">
                            <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                        </html:messages>
                    </div>
                </div>

                <!-- Comment input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="comment"><bean:message key="main.timereport.monthly.taskdescription.text" /></label>
                    <html:textarea styleClass="form-control" styleId="comment" rows="3" property="comment" />
                    <html:messages id="errmsg" property="comment">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Submit button -->
                <button type="submit" class="btn btn-primary btn-block">
                    <i class="bi bi-alarm"></i> <bean:message key="chicoree.btn.save" />
                </button>
                <a href="/do/chicoree/ShowDashboard" class="btn btn-light"><bean:message key="chicoree.btn.cancel" /></a>

            </html:form>
        </div>
    </div>
</div>
<script src="<c:url value="/webjars/jquery/jquery.min.js"/>"></script>
<script src="<c:url value="/webjars/bootstrap/js/bootstrap.min.js"/>"></script>
<script>
  function refreshTimereportFormFields(form, event) {
    form.action = "/do/chicoree/RefreshTimereportFormFields?event=" + event;
    form.submit();
  }
  $('#date').attr('type', 'date');
  <html:messages id="errmsg" property="date">
    $('#date').addClass('is-invalid');
  </html:messages>
  <html:messages id="errmsg" property="orderId">
    $('#orderId').addClass('is-invalid');
  </html:messages>
  <html:messages id="errmsg" property="suborderId">
    $('#suborderId').addClass('is-invalid');
  </html:messages>
  $('#hours').attr('type', 'number');
  <html:messages id="errmsg" property="hours">
    $('#hours').addClass('is-invalid');
  </html:messages>
  $('#minutes').attr('type', 'number');
  <html:messages id="errmsg" property="minutes">
    $('#minutes').addClass('is-invalid');
  </html:messages>
  <html:messages id="errmsg" property="comment">
    $('#comment').addClass('is-invalid');
  </html:messages>
</script>
</body>
</html:html>
