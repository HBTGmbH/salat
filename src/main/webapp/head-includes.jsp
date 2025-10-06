<%@taglib uri="jakarta.tags.core" prefix="c"%>
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
      (function() {
        const refreshUrl = "<c:out value="${salatProperties.auth.refresh.refreshUrl}" />";
        const REFRESH_INTERVAL_MS = 1000 * 60 * 2; // 2 Minuten
        const DEBOUNCE_MS = 10 * 1000; // min. 10s zwischen zusätzlichen Triggern
        let lastTriggerTs = 0;

        function logSuccess() {
          console.log("Token refresh completed successfully.");
        }

        function logFailure(jqXHR, textStatus, errorThrown) {
          var status = jqXHR ? jqXHR.status : "n/a";
          var body = (jqXHR && jqXHR.responseText) ? jqXHR.responseText.substring(0, 500) : "";
          if (status === 401) {
            console.warn("Token refresh returned 401 (unauthorized). Session may be expired.", {
              status: status,
              textStatus: textStatus,
              error: errorThrown
            });
          } else {
            console.error("Token refresh failed.", {
              status: status,
              textStatus: textStatus,
              error: errorThrown,
              responseSnippet: body
            });
          }
        }

        function refreshToken() {
          $.ajax({
            url: refreshUrl,
            method: "GET",
            timeout: 5000,
            cache: false
          }).done(logSuccess).fail(logFailure);
        }

        function triggerRefreshDebounced() {
          var now = Date.now();
          if (now - lastTriggerTs >= DEBOUNCE_MS) {
            lastTriggerTs = now;
            refreshToken();
          }
        }

        // Periodischer Refresh
        setInterval(refreshToken, REFRESH_INTERVAL_MS);

        // Zusätzliche Trigger bei "zurück im Browser/Tab"
        document.addEventListener("visibilitychange", function() {
          if (document.visibilityState === "visible") {
            triggerRefreshDebounced();
          }
        });

        window.addEventListener("focus", function() {
          triggerRefreshDebounced();
        });

        window.addEventListener("pageshow", function() {
          // Wird u.a. gefeuert, wenn die Seite aus dem bfcache kommt
          triggerRefreshDebounced();
        });

        window.addEventListener("online", function() {
          // Nach Wiederherstellung der Verbindung
          triggerRefreshDebounced();
        });
      })();
    </script>
</c:if>
