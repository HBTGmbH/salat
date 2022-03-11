<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%
    Object loginEmployee = session.getAttribute("loginEmployee");
    if(loginEmployee == null) {
        response.sendRedirect("/chicoree/login.jsp");
    }
%>
<html:html>
<head>
    <title>SALAT - chicoree edition - by kr@2022</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="/webjars/bootstrap/css/bootstrap.min.css"
          id="bootstrap-css"></link>
    <link rel="stylesheet" href="/webjars/bootstrap-icons/font/bootstrap-icons.css">
</head>
<body>
<div class="container">
    <html:messages id="msg" message="true">
        <div class="alert alert-danger" role="alert"><bean:write name="msg" /></div>
    </html:messages>
    <div class="card">
        <div class="card-header text-center h5">
            <i class="bi bi-alarm"></i>
            <span>Report Time</span>
        </div>
        <div class="card-body bg-light">
            <html:form action="/chicoree/StoreTimereport">

                <!-- Date input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="date">Date</label>
                    <input type="date" id="date" name="date"
                           onchange="refreshTimereportFormFields(this.form, 'date-selected')"
                           value="<bean:write name="chicoree/TimereportForm" property="date" />"
                           class="form-control <html:messages id="errmsg" property="date">is-invalid</html:messages>" />
                    <html:messages id="errmsg" property="date">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Order input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="order">Order</label>
                    <html:select property="orderId"
                                 styleClass="form-select"
                                 styleId="order"
                                 onchange="refreshTimereportFormFields(this.form, 'date-selected')">
                        <html:option value="" disabled="true">--- select ---</html:option>
                        <html:options collection="orderOptions" labelProperty="label" property="value" />
                    </html:select>
                    <html:messages id="errmsg" property="orderId">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Suborder input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="suborder">Suborder</label>
                    <html:select property="suborderId"
                                 styleClass="form-select"
                                 styleId="suborder">
                        <html:options collection="suborderOptions" labelProperty="label" property="value" />
                    </html:select>
                    <html:messages id="errmsg" property="suborderId">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Duration input -->
                <div class="row g-3 mb-4">
                    <div class="col">
                        <label class="form-label" for="hours">Hours</label>
                        <input type="number" id="hours" name="hours"
                               value="<bean:write name="chicoree/TimereportForm" property="hours" />"
                               class="text-center form-control <html:messages id="errmsg" property="hours">is-invalid</html:messages>" />
                        <html:messages id="errmsg" property="hours">
                            <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                        </html:messages>
                    </div>
                    <div class="col">
                        <label class="form-label" for="minutes">Minutes</label>
                        <input type="number" id="minutes" name="minutes"
                               value="<bean:write name="chicoree/TimereportForm" property="minutes" />"
                               class="text-center form-control <html:messages id="errmsg" property="minutes">is-invalid</html:messages>" />
                        <html:messages id="errmsg" property="minutes">
                            <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                        </html:messages>
                    </div>
                </div>

                <!-- Comment input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="comment">Comment</label>
                    <html:textarea styleClass="form-control" styleId="comment" rows="3" property="comment" />
                    <html:messages id="errmsg" property="comment">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Submit button -->
                <button type="submit" class="btn btn-primary btn-block"><i class="bi bi-alarm"></i> Save</button>
                <a href="dashboard.jsp" class="btn btn-light">Cancel</a>

            </html:form>
        </div>
    </div>
</div>
<script src="/webjars/jquery/jquery.min.js"></script>
<script src="/webjars/bootstrap/js/bootstrap.min.js"></script>
<script>
  function refreshTimereportFormFields(form, event) {
    form.action = "/do/chicoree/RefreshTimereportFormFields?event=" + event;
    form.submit();
  }
</script>
</body>
</html:html>
