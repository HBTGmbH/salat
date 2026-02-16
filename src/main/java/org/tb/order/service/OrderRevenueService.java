package org.tb.order.service;

import static java.util.function.Function.identity;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderRevenue;
import org.tb.order.domain.OrderRevenueExcelMapping;
import org.tb.order.persistence.OrderRevenueExcelMappingRepository;
import org.tb.order.persistence.OrderRevenueRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRevenueService {

  public static final String FIELD_ORDER = "order";
  public static final String FIELD_DATE = "date";
  public static final String FIELD_TYPE = "type";
  public static final String FIELD_AMOUNT = "amount";
  private final OrderRevenueRepository orderRevenueRepository;
  private final OrderRevenueExcelMappingRepository mappingRepository;
  private final CustomerorderService customerorderService;
  private final MessageSource messageSource;

  public List<String> getHeaderColumns(InputStream is) {
    List<String> columns = new ArrayList<>();
    try (Workbook workbook = new XSSFWorkbook(is)) {
      if (workbook.getNumberOfSheets() == 0) {
        return columns;
      }
      Sheet sheet = workbook.getSheetAt(0);
      Iterator<Row> rowIterator = sheet.iterator();
      if (rowIterator.hasNext()) {
        Row headerRow = rowIterator.next();
        for (Cell cell : headerRow) {
          columns.add(getCellValueAsString(cell));
        }
      }
    } catch (Exception e) {
      log.error("Error reading header", e);
    }
    return columns;
  }

  public Map<String, String> getInitialMapping(List<String> columns, Employee employee) {
    Map<String, String> mapping = new HashMap<>();
    List<OrderRevenueExcelMapping> saved = mappingRepository.findAllByEmployeeId(employee.getId());
    Map<String, String> savedMap = saved.stream()
        .collect(Collectors.toMap(OrderRevenueExcelMapping::getSourceColumn, OrderRevenueExcelMapping::getTargetField));

    for (String col : columns) {
      if (savedMap.containsKey(col)) {
        mapping.put(col, savedMap.get(col));
      } else {
        String field = detectField(col);
        if (field != null && !mapping.containsValue(field)) {
          mapping.put(col, field);
        }
      }
    }
    return mapping;
  }

  private String detectField(String headerName) {
    String lower = headerName.toLowerCase();
    if (lower.equals("auftrag") || lower.equals("order") || lower.equals("sign")) {
      return FIELD_ORDER;
    }
    if (lower.equals("datum") || lower.equals("date")) {
      return FIELD_DATE;
    }
    if (lower.equals("art") || lower.equals("type")) {
      return FIELD_TYPE;
    }
    if (lower.equals("betrag") || lower.equals("amount") || lower.equals("umsatz") || lower.equals("kosten")) {
      return FIELD_AMOUNT;
    }
    return null;
  }

  @Transactional
  public OrderRevenueImportResult importData(InputStream is, String comment, Map<String, String> columnMapping) {
    List<OrderRevenueImportResult.RowResult> rowResults = new ArrayList<>();
    int successCount = 0;
    int errorCount = 0;

    try (Workbook workbook = new XSSFWorkbook(is)) {
      if (workbook.getNumberOfSheets() == 0) {
        return OrderRevenueImportResult.builder().rowResults(Collections.emptyList()).build();
      }
      Sheet sheet = workbook.getSheetAt(0);
      Iterator<Row> rowIterator = sheet.iterator();

      if (!rowIterator.hasNext()) {
        return OrderRevenueImportResult.builder().rowResults(Collections.emptyList()).build();
      }

      Row headerRow = rowIterator.next();
      Map<Integer, String> columnIndexToField = new HashMap<>();
      for (Cell cell : headerRow) {
        String headerName = getCellValueAsString(cell);
        if (columnMapping.containsKey(headerName)) {
          columnIndexToField.put(cell.getColumnIndex(), columnMapping.get(headerName));
        }
      }

      Set<String> customerorderSigns = customerorderService.getAllCustomerorders().stream().map(Customerorder::getSign)
          .collect(Collectors.toSet());
      Set<String> seenInFile = new HashSet<>();

      var formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

      while (rowIterator.hasNext()) {
        Row row = rowIterator.next();
        if (isRowEmpty(row)) {
          continue;
        }

        Set<OrderRevenueImportRow> importRows = parseRow(row, columnIndexToField, formulaEvaluator);
        List<String> errors = validateRow(importRows, customerorderSigns, seenInFile);

        if (errors.isEmpty()) {
          for (var importRow : importRows) {
            var customerorder = customerorderService.getCustomerorderBySign(importRow.getOrderSign());
            saveOrderRevenue(importRow, customerorder, comment);
            rowResults.add(OrderRevenueImportResult.RowResult.builder().rowIndex(row.getRowNum() + 1)
                .content(importRow.getOriginalRowContent()).success(true).errors(Collections.emptyList()).build());
          }
          successCount++;
        } else {
          // only the first parsed row is important
          rowResults.add(OrderRevenueImportResult.RowResult.builder().rowIndex(row.getRowNum() + 1)
              .content(importRows.iterator().next().getOriginalRowContent()).success(false).errors(errors).build());
          errorCount++;
        }
      }
    } catch (Exception e) {
      log.error("Error importing Excel data", e);
      throw new RuntimeException("Error importing Excel data", e);
    }

    return OrderRevenueImportResult.builder().rowResults(rowResults).successCount(successCount).errorCount(errorCount)
        .build();
  }

  private boolean isRowEmpty(Row row) {
    if (row == null) {
      return true;
    }
    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
      Cell cell = row.getCell(c);
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        return false;
      }
    }
    return true;
  }

  public void saveMappings(Employee employee, Map<String, String> columnMapping) {
    List<OrderRevenueExcelMapping> existing = mappingRepository.findAllByEmployeeId(employee.getId());
    var existingBySource = existing.stream().collect(Collectors.toMap(m -> m.getSourceColumn(), identity()));

    for (Map.Entry<String, String> entry : columnMapping.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        if (existingBySource.containsKey(entry.getKey())) {
          mappingRepository.delete(existingBySource.get(entry.getKey()));
        }
      } else if (existingBySource.containsKey(entry.getKey())) {
        var m = existingBySource.get(entry.getKey());
        m.setTargetField(entry.getValue());
      } else {
        OrderRevenueExcelMapping m = new OrderRevenueExcelMapping();
        m.setEmployee(employee);
        m.setSourceColumn(entry.getKey());
        m.setTargetField(entry.getValue());
        mappingRepository.save(m);
      }
    }
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return "";
    }
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        var numValue = cell.getNumericCellValue();
        var bd = BigDecimal.valueOf(numValue);
        if (bd.stripTrailingZeros().scale() <= 0) {
          yield String.valueOf(bd.longValueExact());
        }
        yield String.valueOf(numValue);
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> {
        try {
          yield cell.getStringCellValue();
        } catch (Exception e) {
          yield String.valueOf(cell.getNumericCellValue());
        }
      }
      default -> "";
    };
  }

  private Set<OrderRevenueImportRow> parseRow(Row row, Map<Integer, String> columnIndexToField, FormulaEvaluator formulaEvaluator) {
    OrderRevenueImportRow importRow = new OrderRevenueImportRow();
    importRow.setRowIndex(row.getRowNum() + 1);
    StringBuilder content = new StringBuilder();

    for (Map.Entry<Integer, String> entry : columnIndexToField.entrySet()) {
      Cell cell = row.getCell(entry.getKey());

      switch (entry.getValue()) {
        case FIELD_ORDER -> {
          String value = parseOrder(cell);
          importRow.setOrderSign(value);
          content.append("Order: ").append(value).append(" ");
        }
        case FIELD_DATE -> {
          LocalDate value = parseDate(cell);
          importRow.setDate(value);
          content.append("Date: ").append(value).append(" ");
        }
        case FIELD_TYPE -> {
          String value = parseType(cell);
          importRow.setType(value);
          content.append("Type: ").append(value).append(" ");
        }
        case FIELD_AMOUNT -> {
          BigDecimal value = parseAmount(cell, formulaEvaluator);
          importRow.setAmount(value);
          content.append("Amount: ").append(value).append(" ");
        }
      }
    }
    importRow.setOriginalRowContent(content.toString().trim());

    if (importRow.getOrderSign().contains(",")) {
      // split amount among orders
      var orders = Arrays.stream(importRow.getOrderSign().split(",")).map(String::trim).toList();
      var amountDivided = importRow.getAmount().divide(BigDecimal.valueOf(orders.size()), RoundingMode.HALF_UP);
      // ensure divided amount does not produce roundng problems, last gets the diff
      var correction = importRow.getAmount().subtract(amountDivided.multiply(BigDecimal.valueOf(orders.size())));
      var rows = orders.stream().map(
          order -> new OrderRevenueImportRow(order, importRow.getDate(), importRow.getType(), amountDivided,
              importRow.getOriginalRowContent(), importRow.getRowIndex())).toList();
      rows.getLast().setAmount(rows.getLast().getAmount().add(correction));
      return Set.copyOf(rows);
    } else {
      return Set.of(importRow);
    }
  }

  private String parseType(Cell cell) {
    return getCellValueAsString(cell);
  }

  private String parseOrder(Cell cell) {
    var value = getCellValueAsString(cell);
    return value;
  }

  private LocalDate parseDate(Cell cell) {
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().toLocalDate();
    }
    String val = getCellValueAsString(cell);
    if (val.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(val);
    } catch (DateTimeParseException e) {
      // Versuche dd.MM.yyyy
      try {
        String[] parts = val.split("\\.");
        if (parts.length == 3) {
          int day = Integer.parseInt(parts[0]);
          int month = Integer.parseInt(parts[1]);
          int year = Integer.parseInt(parts[2]);
          if (year < 100) {
            year += 2000;
          }
          return LocalDate.of(year, month, day);
        }
      } catch (Exception ex) {
        // ignore
      }
      return null;
    }
  }

  private BigDecimal parseAmount(Cell cell, FormulaEvaluator formulaEvaluator) {
    if (cell == null) {
      return null;
    }
    var cellType = formulaEvaluator.evaluateFormulaCell(cell);
    if (cellType == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
    }
    String val = getCellValueAsString(cell).replace(".", "").replace(",", ".");
    if (val.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(val).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private List<String> validateRow(Set<OrderRevenueImportRow> rows, Set<String> orderSigns, Set<String> seenInFile) {
    List<String> errors = new ArrayList<>();
    var locale = LocaleContextHolder.getLocale();

    for (var row : rows) {
      if (row.getOrderSign() == null || row.getOrderSign().isEmpty()) {
        errors.add(
            messageSource.getMessage("orderrevenue.upload.error.missing.column", new Object[]{FIELD_ORDER}, locale));
      } else if (!orderSigns.contains(row.getOrderSign())) {
        errors.add(messageSource.getMessage("orderrevenue.upload.error.customerorder.not.found",
            new Object[]{row.getOrderSign()}, locale));
      }

      if (row.getDate() == null) {
        errors.add(messageSource.getMessage("orderrevenue.upload.error.date.invalid", new Object[]{""}, locale));
      }

      if (row.getAmount() == null) {
        errors.add(messageSource.getMessage("orderrevenue.upload.error.amount.not.numeric", new Object[]{""}, locale));
      }

      if (row.getType() == null || row.getType().isEmpty()) {
        errors.add(
            messageSource.getMessage("orderrevenue.upload.error.missing.column", new Object[]{FIELD_TYPE}, locale));
      }

      if (errors.isEmpty()) {
        String key = row.getOrderSign() + "|" + row.getDate() + "|" + row.getType();
        if (seenInFile.contains(key)) {
          errors.add(
              messageSource.getMessage("orderrevenue.upload.error.duplicate.in.file", new Object[]{row.getRowIndex()},
                  locale));
        } else {
          seenInFile.add(key);
        }
      }
    }

    return errors;
  }

  private void saveOrderRevenue(OrderRevenueImportRow row, Customerorder customerorder, String comment) {
    Optional<OrderRevenue> existing = orderRevenueRepository.findByCustomerorderIdAndDateAndType(customerorder.getId(),
        row.getDate(), row.getType());
    OrderRevenue revenue = existing.orElse(new OrderRevenue());
    revenue.setCustomerorder(customerorder);
    revenue.setDate(row.getDate());
    revenue.setType(row.getType());
    revenue.setAmount(row.getAmount());
    revenue.setComment(comment);
    orderRevenueRepository.save(revenue);
  }
}
