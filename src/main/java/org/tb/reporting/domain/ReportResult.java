package org.tb.reporting.domain;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

@Getter
@Builder
public class ReportResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private final List<ReportParameter> parameters;
  private final List<ReportResultColumnHeader> columnHeaders;

  @Singular
  private final List<ReportResultRow> rows;

  /**
   * Liefert ein JavaScript-kompatibles Array als String,
   * bei dem die Objekt-Keys nicht in Anführungszeichen stehen.
   * Hinweis: Das Ergebnis ist KEIN valides JSON, sondern JS-Objektliteral-Syntax.
   */
  public String toJavaScriptArrayLiteral() {
    StringBuilder js = new StringBuilder("[");
    for (int i = 0; i < rows.size(); i++) {
      ReportResultRow row = rows.get(i);
      Map<String, ReportResultColumnValue> values = row.getColumnValues();
      js.append("{");
      boolean first = true;
      for (ReportResultColumnHeader header : columnHeaders) {
        if (!first) {
          js.append(",");
        }
        first = false;

        String rawKey = header.getName();
        String jsKey = sanitizeJsIdentifier(rawKey);
        js.append(jsKey).append(":");

        ReportResultColumnValue value = values.get(rawKey);
        if (value == null || value.getValue() == null) {
          js.append("null");
        } else {
          Object v = value.getValue();
          if (v instanceof String s) {
            js.append("\"").append(escapeJsString(s)).append("\"");
          } else if (v instanceof java.util.Date d) {
            String iso = DateTimeFormatter.ISO_INSTANT.format(d.toInstant());
            js.append("\"").append(iso).append("\"");
          } else if (v instanceof Character c) {
            js.append("\"").append(escapeJsString(String.valueOf(c))).append("\"");
          } else if (v instanceof Number || v instanceof Boolean) {
            js.append(v.toString());
          } else {
            js.append("\"").append(escapeJsString(value.getValueAsString())).append("\"");
          }
        }
      }
      js.append("}");
      if (i < rows.size() - 1) {
        js.append(",");
      }
    }
    js.append("]");
    return js.toString();
  }

  private static String sanitizeJsIdentifier(String key) {
    if (key == null || key.isEmpty()) return "\"\"";
    if (!isJsIdentifierStart(key.charAt(0))) {
      return "\"" + escapeJsString(key) + "\"";
    }
    for (int i = 1; i < key.length(); i++) {
      if (!isJsIdentifierPart(key.charAt(i))) {
        return "\"" + escapeJsString(key) + "\"";
      }
    }
    return key;
  }

  private static boolean isJsIdentifierStart(char ch) {
    return ch == '$' || ch == '_' || Character.isLetter(ch);
  }

  private static boolean isJsIdentifierPart(char ch) {
    return ch == '$' || ch == '_' || Character.isLetterOrDigit(ch);
  }

  private static String escapeJsString(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (ch < 0x20) {
            sb.append(String.format("\\u%04x", (int) ch));
          } else {
            sb.append(ch);
          }
        }
      }
    }
    return sb.toString();
  }

}
