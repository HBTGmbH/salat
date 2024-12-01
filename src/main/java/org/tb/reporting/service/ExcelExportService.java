package org.tb.reporting.service;

import jakarta.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.GlobalConstants;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnValue;

@Service
@Transactional
@RequiredArgsConstructor
public class ExcelExportService {

  public static final String CELL_STYLE_DATE_KEY = "Date";
  public static final String CELL_STYLE_DATETIME_KEY = "DateTime";

  public byte[] exportToExcel(ReportResult reportResult) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream(); Workbook workbook = createExcel(reportResult)) {
      workbook.write(out);
      return out.toByteArray();
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
      case "Double" -> cell.setCellValue((double) columnValue.getValue());
      case "Float" -> cell.setCellValue((float) columnValue.getValue());
      case "Long" -> cell.setCellValue((long) columnValue.getValue());
      case "Integer" -> cell.setCellValue((int) columnValue.getValue());
      case "BigDecimal" -> cell.setCellValue(((BigDecimal) columnValue.getValue()).doubleValue());
      default -> cell.setCellValue(columnValue.getValueAsString());
    }
  }

  private CellType getCellType(ReportResultColumnValue columnValue) {
    if (columnValue.getValue() == null) return CellType.BLANK;
    return "String".equals(columnValue.getValue().getClass().getSimpleName())
        ? CellType.STRING
        : CellType.NUMERIC;
  }

}
