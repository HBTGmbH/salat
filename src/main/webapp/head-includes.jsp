<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

<link rel="shortcut icon" type="image/x-icon" href="/favicon.ico" />

<link rel="stylesheet" type="text/css" href="<c:url value="/style/tb.css" />" media="all" />
<link rel="stylesheet" href="<c:url value="/style/select2.min.css" />" />

<script src="<c:url value="/scripts/jquery-1.11.3.min.js" />" type="text/javascript"></script>
<script src="<c:url value="/scripts/select2.full.min.js" />" type="text/javascript"></script>
<script src="<c:url value="/scripts/favouriteOrder.js" />" type="text/javascript"></script>
<script src="<c:url value="/scripts/massedit.js" />" type="text/javascript"></script>

<c:if test="${salatProperties.auth.refresh.enabled}">
    <script type="text/javascript">
      setInterval(function() {
        let refreshUrl = "<c:out value="${salatProperties.auth.refresh.refreshUrl}" />";
        $.ajax(refreshUrl).done(function() {
          console.log("Token refresh completed successfully.");
        }).fail(function() {
          console.log("Token refresh failed. See application logs for details.");
        });
      }, 1000 * 60 * 2);
    </script>
</c:if>
