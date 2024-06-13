package org.tb.reporting.action;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.reporting.action.ExecuteReportForm.ReportParameter;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnValue;
import org.tb.reporting.service.ReportingService;

import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.countMatches;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteReportAction extends LoginRequiredAction<ExecuteReportForm> {

    public static final String CELL_STYLE_DATE_KEY = "Date";
    public static final String CELL_STYLE_DATETIME_KEY = "DateTime";
    private final ReportingService reportingService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ExecuteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var reportDefinition = reportingService.getReportDefinition(form.getReportId());

        if ("setParameters".equals(request.getParameter("task"))) {
            var reportResult = reportingService.execute(form.getReportId(), getParameterMap(form.getParameters()));
            request.getSession().setAttribute("report", reportDefinition);
            request.getSession().setAttribute("reportParameters", nonEmpty(form.getParameters()));
            request.getSession().setAttribute("reportResult", reportResult);
            return mapping.findForward("showReportResult");
        } else if ("export".equals(request.getParameter("task"))) {
            var reportResult = (ReportResult) request.getSession().getAttribute("reportResult");
            exportToExcel(reportDefinition, reportResult, response);
            return null;
        } else {
            var parametersInReport = countMatches(reportDefinition.getSql(), ':');
            var parametersFromRequest = nonEmpty(getParametersFromRequest(request, reportDefinition.getSql()));

            if (parametersInReport > 0 && parametersInReport > parametersFromRequest.size()) {
                // show parameters dialog
                request.getSession().setAttribute("report", reportDefinition);
                return mapping.findForward("showReportParameters");
            } else {
                var reportResult = reportingService.execute(form.getReportId(), getParameterMap(parametersFromRequest));
                request.getSession().setAttribute("report", reportDefinition);
                request.getSession().setAttribute("reportResult", reportResult);
                return mapping.findForward("showReportResult");
            }
        }

    }

    private void exportToExcel(ReportDefinition reportDefinition, ReportResult reportResult, HttpServletResponse response) {
        try (ServletOutputStream out = response.getOutputStream(); Workbook workbook = createExcel(reportResult)) {
            response.setHeader("Content-disposition", "attachment; filename=\"" + createFilename(reportDefinition) + "\"");
            response.setContentType(GlobalConstants.INVOICE_EXCEL_NEW_CONTENT_TYPE);
            workbook.write(out);
        } catch (IOException e) {
            log.warn("Could not write excel export to output stream", e);
        }
    }

    private Workbook createExcel(ReportResult reportResult) {
        XSSFWorkbook workbook = new XSSFWorkbook();

        var cellStyles = new HashMap<String, CellStyle>();
        var dateCellStyle = workbook.createCellStyle();
        var dateFormat = workbook.createDataFormat();
        dateCellStyle.setDataFormat(dateFormat.getFormat(GlobalConstants.DEFAULT_EXCEL_DATE_FORMAT));
        cellStyles.put(CELL_STYLE_DATE_KEY, dateCellStyle);
        dateCellStyle = workbook.createCellStyle();
        dateFormat = workbook.createDataFormat();
        dateCellStyle.setDataFormat(dateFormat.getFormat(GlobalConstants.DEFAULT_EXCEL_DATETIME_FORMAT));
        cellStyles.put(CELL_STYLE_DATETIME_KEY, dateCellStyle);

        var sheet = workbook.createSheet("result");
        XSSFRow headerRow = sheet.createRow(0);
        for (int headerIndex = 0; headerIndex < reportResult.getColumnHeaders().size(); headerIndex++) {
            var header = reportResult.getColumnHeaders().get(headerIndex);
            var cell = headerRow.createCell(headerIndex, CellType.STRING);
            cell.setCellValue(header.getName());
        }
        int dataRowIndex = 1;
        for (int rowIndex = 0; rowIndex < reportResult.getRows().size(); rowIndex++) {
            var row = reportResult.getRows().get(rowIndex);
            var dataRow = sheet.createRow(dataRowIndex);
            for (int columnIndex = 0; columnIndex < reportResult.getColumnHeaders().size(); columnIndex++) {
                var column = reportResult.getColumnHeaders().get(columnIndex);
                var columnValue = row.getColumnValues().get(column.getName());
                XSSFCell cell = dataRow.createCell(columnIndex, getCellType(columnValue));
                setCellValue(cell, columnValue, cellStyles);
            }
            dataRowIndex++;
        }
        return workbook;
    }

    private void setCellValue(XSSFCell cell, ReportResultColumnValue columnValue, Map<String, CellStyle> cellStyles) {
        if (columnValue.getValue() == null) return;
        switch (columnValue.getValue().getClass().getSimpleName()) {
            case "LocalDate" -> {
                cell.setCellValue((LocalDate) columnValue.getValue());
                cell.setCellStyle(cellStyles.get(CELL_STYLE_DATE_KEY));
            }
            case "LocalDateTime" -> {
                cell.setCellValue((LocalDateTime) columnValue.getValue());
                cell.setCellStyle(cellStyles.get(CELL_STYLE_DATETIME_KEY));
            }
            case "Double" -> cell.setCellValue((Double) columnValue.getValue());
            case "Float" -> cell.setCellValue((Float) columnValue.getValue());
            case "Long" -> cell.setCellValue((Long) columnValue.getValue());
            case "Integer" -> cell.setCellValue((Integer) columnValue.getValue());
            default -> cell.setCellValue(columnValue.getValueAsString());
        }
    }

    private CellType getCellType(ReportResultColumnValue columnValue) {
        if (columnValue.getValue() == null) return CellType.BLANK;
        return "String".equals(columnValue.getValue().getClass().getSimpleName())
                ? CellType.STRING
                : CellType.NUMERIC;
    }

    private String createFilename(ReportDefinition reportDefinition) {
        return "report-" +
                reportDefinition.getName()
                        .replaceAll("\\(", "_")
                        .replaceAll("\\)", "_")
                        .replaceAll(" ", "_")
                        .replaceAll(":", "_") +
                "-" + DateUtils.formatDateTime(DateUtils.now(), "dd-MM-yy-HHmm") +
                ".xlsx";
    }

    private static List<ReportParameter> nonEmpty(List<ReportParameter> parameters) {
        return parameters.stream().filter(p -> p.getName() != null && !p.getName().isBlank()).toList();
    }

    private static Map<String, Object> getParameterMap(List<ReportParameter> parameters) {
        var result = new HashMap<String, Object>();
        for (ReportParameter parameter : nonEmpty(parameters)) {
            switch (parameter.getType()) {
                case "string" -> result.put(parameter.getName(), parameter.getValue());
                case "date" -> result.put(parameter.getName(), DateUtils.parse(parameter.getValue()));
                case "number" -> result.put(parameter.getName(), parameter.getValue());
            }
        }
        return result;
    }

    private static ReportParameter toReportParameter(String key, String value) {
        if (value.indexOf(',') > 0) {
            var parts = value.split(",", 2);
            return ReportParameter.builder().name(key).type(parts[0].trim()).value(parts[1].trim()).build();
        }
        return ReportParameter.builder().name(key).type("string").value(value.trim()).build();
    }

    private static List<ReportParameter> getParametersFromRequest(HttpServletRequest request, String sql) {
        return request.getParameterMap()
                .entrySet()
                .stream()
                .filter(parameter -> sql.contains(":" + parameter.getKey()))
                .map(parameter -> toReportParameter(parameter.getKey(), join(",", parameter.getValue())))
                .toList();
    }

}
