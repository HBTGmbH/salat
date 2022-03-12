<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<html:html>
<head>
    <title><bean:message key="chicoree.product.title" /></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="/webjars/bootstrap/css/bootstrap.min.css"
          id="bootstrap-css"></link>
    <link rel="stylesheet" href="/webjars/bootstrap-icons/font/bootstrap-icons.css">
</head>
<body>
<div class="container" style="max-width: 500px">
    <div class="card mt-4 mb-4">
        <img src="/chicoree/images/chicoree-salat.jpeg" class="card-img-top" alt="lovely">
        <div class="card-body bg-light">
            <html:form action="/chicoree/Login">

                <html:messages id="msg" message="true">
                    <div class="alert alert-danger" role="alert"><bean:write name="msg" /></div>
                </html:messages>

                <!-- Email input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="loginname"><bean:message key="main.general.employeesign.text" /></label>
                    <html:text styleId="loginname" property="loginname" styleClass="form-control" />
                    <html:messages id="errmsg" property="loginname">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Password input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="password"><bean:message key="main.general.password.text" /></label>
                    <html:password styleId="password" property="password" styleClass="form-control" />
                    <html:messages id="errmsg" property="password">
                        <div class="invalid-feedback">
                            <bean:write name="errmsg" />
                        </div>
                    </html:messages>
                </div>

                <!-- Submit button -->
                <button type="submit" class="btn btn-primary btn-block"><i class="bi bi-door-open"></i> Sign in</button>

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
  $('#loginname').attr('placeholder', '<bean:message key="chicoree.placeholder.loginname" />');
  <html:messages id="errmsg" property="loginname">
    $('#loginname').addClass('is-invalid');
  </html:messages>
  <html:messages id="errmsg" property="password">
    $('#password').addClass('is-invalid');
  </html:messages>
</script>
</body>
</html:html>
