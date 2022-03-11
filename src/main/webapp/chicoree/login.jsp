<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<html:html>
<head>
    <title>SALAT - chicoree edition - by kr@2022</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="/webjars/bootstrap/css/bootstrap.min.css"
          id="bootstrap-css"></link>
    <link rel="stylesheet" href="/webjars/bootstrap-icons/font/bootstrap-icons.css">
</head>
<body>
<div class="container">
    <div class="card">
        <img src="/chicoree/images/chicoree-salat.jpeg" class="card-img-top" alt="lovely">
        <div class="card-body bg-light">
            <html:form action="/chicoree/Login">

                <html:messages id="msg" message="true">
                    <div class="alert alert-danger" role="alert"><bean:write name="msg" /></div>
                </html:messages>

                <!-- Email input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="form2Example1">Loginname</label>
                    <input type="text" id="form2Example1" name="loginname"
                           value="<bean:write name="chicoree/LoginForm" property="loginname" />"
                           class="form-control <html:messages id="errmsg" property="loginname">is-invalid</html:messages>"
                           placeholder="your sign, e.g. kr"/>
                    <html:messages id="errmsg" property="loginname">
                        <div class="invalid-feedback"><bean:write name="errmsg" /></div>
                    </html:messages>
                </div>

                <!-- Password input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="form2Example1">Password</label>
                    <input type="password" id="form2Example2" name="password" class="form-control" />
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
</body>
</html:html>
