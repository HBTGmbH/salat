package org.tb.reporting.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;
import static org.tb.common.exception.ErrorCode.RP_REPORT_PARAMETER_INVALID;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.reporting.domain.ReportParameter;

/**
 * Die Parameter eines Reports, wie sie aus einer Anfrage kommen. Ein Report nennt seine Parameter
 * als <code>:name</code> im SQL; ein Wert trägt seinen Typ als Präfix (<code>date,2025-09-01</code>),
 * ohne Präfix gilt <code>string</code>.
 *
 * <p>Diese Logik lag als statische Methoden im {@code ReportController}. Seit es neben der Weboberfläche
 * auch einen API-Zugang gibt, wird sie von zwei Seiten gebraucht und liegt deshalb hier.
 */
public final class ReportParameters {

  private static final Pattern PLACEHOLDER = Pattern.compile(":\\w+");

  private ReportParameters() {
  }

  /** Parameter ohne Namen sind leere Formularzeilen und zählen nicht. */
  public static List<ReportParameter> nonEmpty(List<ReportParameter> parameters) {
    return parameters.stream().filter(p -> p.getName() != null && !p.getName().isBlank()).toList();
  }

  /**
   * Liest aus den Anfrageparametern diejenigen heraus, die das SQL des Reports auch nennt — alles
   * andere ist für den Report bedeutungslos und wird nicht weitergegeben.
   */
  public static List<ReportParameter> fromRequest(Map<String, String> params, String query) {
    if (query == null) {
      return emptyList();
    }
    return params.entrySet().stream()
        .filter(e -> query.contains(":" + e.getKey()))
        .map(e -> toReportParameter(e.getKey(), e.getValue()))
        .toList();
  }

  /** Die Namen der Parameter, die das SQL nennt, für die aber kein Wert vorliegt. */
  public static Set<String> missing(List<ReportParameter> parameters, String query) {
    if (query == null) {
      return emptySet();
    }
    var parameterNames = parameters.stream().map(ReportParameter::getName).toList();
    return PLACEHOLDER
        .matcher(query)
        .results()
        .map(MatchResult::group)
        .map(qp -> qp.substring(1))
        .filter(not(parameterNames::contains))
        .collect(toSet());
  }

  /** Die Werte in der Form, die {@code NamedParameterJdbcTemplate} erwartet. */
  public static Map<String, Object> toParameterMap(List<ReportParameter> parameters) {
    var result = new HashMap<String, Object>();
    for (ReportParameter parameter : nonEmpty(parameters)) {
      var name = parameter.getName();
      var value = parameter.getValue();
      if (value == null || value.isBlank()) {
        value = "";
      }
      value = value.replace('*', '%'); // make it SQL compatible
      switch (parameter.getType()) {
        case "date" -> result.put(name, toDate(name, value));
        default -> result.put(name, value);
      }
    }
    return result;
  }

  private static LocalDate toDate(String name, String value) {
    if (value.equals("TODAY") || value.equals("HEUTE")) {
      return today();
    }
    if (value.isBlank()) {
      return null;
    }
    try {
      return DateUtils.parse(value);
    } catch (DateTimeParseException e) {
      // Ein Aufrufer, der einen unbrauchbaren Wert schickt, soll das erfahren; ohne diese Umsetzung
      // schlägt die Ausführung erst tief im JDBC-Template fehl.
      throw new InvalidDataException(RP_REPORT_PARAMETER_INVALID, name, value);
    }
  }

  private static ReportParameter toReportParameter(String key, String value) {
    if (value.indexOf(',') > 0) {
      var parts = value.split(",", 2);
      return ReportParameter.builder().name(key).type(parts[0].trim()).value(parts[1].trim()).build();
    }
    return ReportParameter.builder().name(key).type("string").value(value.trim()).build();
  }

}
