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
        <div class="card-header text-center h5">
            <i class="bi bi-alarm"></i>
            <span>Report Time</span>
        </div>
        <div class="card-body bg-light">
            <form action="dashboard.jsp">

                <!-- Date input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="date">Date</label>
                    <input type="date" id="date" class="form-control is-invalid" />
                    <div class="invalid-feedback">
                        Please enter a date.
                    </div>
                </div>

                <!-- Order input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="order">Order</label>
                    <select class="form-select" id="order">
                        <option value="10">1400 iSell</option>
                        <option value="1">1500 WIVA Whatever</option>
                        <option value="2">KRANK</option>
                        <option value="3" selected>URLAUB</option>
                    </select>
                </div>

                <!-- Suborder input -->
                <div class="form-outline mb-4">
                    <label class="form-label" for="suborder">Suborder</label>
                    <select class="form-select" id="suborder">
                        <option value="10">2021</option>
                        <option value="1" selected>2022</option>
                    </select>
                </div>

                <!-- duration input -->
                <div class="row g-3 mb-4">
                    <div class="col">
                        <label class="form-label" for="hours">Hours</label>
                        <input type="number" id="hours" class="form-control" />
                    </div>
                    <div class="col">
                        <label class="form-label" for="minutes">Minutes</label>
                        <input type="number" id="minutes" class="form-control" />
                    </div>
                </div>

                <!-- Submit button -->
                <button type="submit" class="btn btn-primary btn-block"><i class="bi bi-alarm"></i> Save</button>
                <a href="dashboard.jsp" class="btn btn-light">Cancel</a>

            </form>
        </div>
    </div>
</div>
<script src="/webjars/jquery/jquery.min.js"></script>
<script src="/webjars/bootstrap/js/bootstrap.min.js"></script>
</body>
</html:html>
