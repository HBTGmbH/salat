package org.tb.reporting.service;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.util.List;
import org.tb.common.util.DateTimeUtils;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;

/**
 * Der Dateiname eines Report-Downloads: Reportname, die verwendeten Parameterwerte und der
 * Zeitpunkt des Abzugs. Excel-Export und API-Abzug benennen ihre Datei gleich.
 */
public final class ReportFileNames {

  private ReportFileNames() {
  }

  public static String create(ReportDefinition reportDefinition, List<ReportParameter> parameters, String extension) {
    var dateTime = DateTimeUtils.now().truncatedTo(SECONDS).toString();
    var fileName = "report-" + reportDefinition.getName() + "-" + toString(parameters) + "-" + dateTime + "." + extension;
    return normalizeToFileName(fileName);
  }

  private static String normalizeToFileName(String fileName) {
    // may produce long ___ sequences
    var withoutSpecialChars = fileName.replaceAll("[^a-zA-Z0-9-_,\\.\\[\\]]", "_").trim();
    // reduce ___ sequences to _
    String result;
    String reduced = withoutSpecialChars;
    do {
      result = reduced;
      reduced = result.replace("__", "_");
    } while (reduced.length() != result.length());
    result = result.replace("-_", "-").replace("_-", "-").replace(",_", "-");
    return result;
  }

  private static String toString(List<ReportParameter> parameters) {
    return parameters.stream().map(ReportParameter::getValue).toList().toString();
  }

}
