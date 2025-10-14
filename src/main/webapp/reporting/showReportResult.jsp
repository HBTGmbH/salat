<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="jakarta.tags.core" prefix="c"%>
<%@taglib uri="http://struts.apache.org/tags-html-el" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@taglib uri="http://hbt.de/jsp/taglib/java8-date-formatting" prefix="java8"%>
<html:html>
    <head>
        <title><bean:message key="main.general.application.title" /> - <bean:message key="main.general.mainmenu.reporting.text" /></title>
        <jsp:include flush="true" page="/head-includes.jsp" />
        <script src="<c:url value="/webjars/plotly.js-dist/plotly.js"/>"></script>
    </head>
    <body>
    <jsp:include flush="true" page="/menu.jsp">
        <jsp:param name="title" value="Menu" />
    </jsp:include>
    <br>
    <span style="font-size:14pt;font-weight:bold;"><br><bean:message key="main.general.mainmenu.reporting.text" />: <c:out value="${report.name}" /><br></span>
    <br>
    <span style="color:red"><html:errors footer="<br>" /> </span>

    <div>
        <details>
            <summary>Show SQL</summary>
            <code>SQL: <c:out value="${report.sql}"/></code>
            <c:forEach var="parameter" items="${reportParameters}">
                <br><code><c:out value="${parameter.name}"/> (<c:out value="${parameter.type}"/>) =
                <c:out value="${parameter.value}"/></code>
            </c:forEach>
        </details>
    </div>

    <table class="center backgroundcolor">
        <tr>
            <c:forEach var="column" items="${reportResult.columnHeaders}" varStatus="statusID">
                <th align="left"><b><c:out value="${column.name}" /></b></th>
            </c:forEach>
        </tr>

        <c:forEach var="row" items="${reportResult.rows}" varStatus="statusID">
            <c:choose>
                <c:when test="${statusID.count%2==0}">
                    <tr class="primarycolor">
                </c:when>
                <c:otherwise>
                    <tr class="secondarycolor">
                </c:otherwise>
            </c:choose>
            <c:forEach var="column" items="${reportResult.columnHeaders}" varStatus="statusID">
                <td align="left"><b><c:out value="${row.columnValues[column.name].valueAsString}" /></b></td>
            </c:forEach>
            </tr>
        </c:forEach>
    </table>
    <html:form action="/ExecuteReport?task=export">
        <html:submit styleId="button">
            <bean:message key="main.reporting.button.export.text" />
        </html:submit>
        <html:hidden property="reportId" />
    </html:form>
    <br><br><br>

    <h2>SQL-Ergebnis visualisieren</h2>

    <div>
        <label>X-Achse:</label>
        <select id="xSelect"></select>

        <label>Y-Achse:</label>
        <select id="ySelect"></select>

        <label>Datenreihe (optional):</label>
        <select id="groupSelect"></select>

        <label>Diagrammtyp:</label>
        <select id="chartType">
            <option value="lines">Linie</option>
            <option value="bar">Balken</option>
            <option value="markers">Punkte (Scatter)</option>
        </select>

        <button id="drawBtn">Zeichnen</button>
    </div>

    <div id="chart" style="width:100%;height:600px;"></div>

    <script>
      // --- Daten
      const data = <c:out value="${reportResult.toJavaScriptArrayLiteral()}" default="[]" escapeXml="false" />;

      // --- Spaltennamen aus den Keys der ersten Zeile ermitteln ---
      const columns = Object.keys(data[0]);

      const xSel = document.getElementById('xSelect');
      const ySel = document.getElementById('ySelect');
      const gSel = document.getElementById('groupSelect');
      const typeSel = document.getElementById('chartType');

      const opt = document.createElement('option');
      opt.value = '';
      opt.textContent = '';
      gSel.appendChild(opt.cloneNode(true));

      columns.forEach(col => {
        [xSel, ySel, gSel].forEach(sel => {
          const opt = document.createElement('option');
          opt.value = col;
          opt.textContent = col;
          sel.appendChild(opt.cloneNode(true));
        });
      });

      document.getElementById('drawBtn').addEventListener('click', () => {
        const xKey = xSel.value;
        const yKey = ySel.value;
        const gKey = gSel.value;
        const chartType = typeSel.value;

        let traces = [];

        if (gKey) {
          const groups = [...new Set(data.map(d => d[gKey]))];
          traces = groups.map(group => ({
            x: data.filter(d => d[gKey] === group).map(d => d[xKey]),
            y: data.filter(d => d[gKey] === group).map(d => d[yKey]),
            type: chartType === "bar" ? "bar" : "scatter",
            mode: chartType === "bar" ? undefined : chartType,
            name: group
          }));
        } else {
          traces = [{
            x: data.map(d => d[xKey]),
            y: data.map(d => d[yKey]),
            type: chartType === "bar" ? "bar" : "scatter",
            mode: chartType === "bar" ? undefined : chartType,
            name: `${yKey} vs ${xKey}`
          }];
        }

        Plotly.newPlot('chart', traces, {
          title: yKey + " vs " + xKey + (gKey ? " grouped by " + gKey : ""),
          xaxis: { title: xKey },
          yaxis: { title: yKey }
        });
      });
    </script>

    </body>
</html:html>
