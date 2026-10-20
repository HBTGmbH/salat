package de.hbt.salat.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.StaticMessageSource;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.invoice.domain.InvoiceData;
import de.hbt.salat.invoice.domain.InvoiceGroup;
import de.hbt.salat.invoice.domain.InvoiceGrouping;
import de.hbt.salat.invoice.domain.InvoiceLine;
import de.hbt.salat.invoice.domain.InvoiceSuborder;
import de.hbt.salat.invoice.domain.InvoiceTimereport;
import de.hbt.salat.invoice.service.ExcelExportService.InvoiceColumnHeaders;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;

/** The Excel export puts every total under the column its title names. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ExcelExportServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);

  @Test
  void the_grand_total_stands_under_the_duration_column_also_with_target_hours() throws Exception {
    var options = InvoiceOptions.builder()
        .showTimereports(true).showEmployee(true).showTaskdescriptions(true).showBudget(true)
        .build();

    var sheet = export(invoiceOfTwoHours(options), options);

    var titles = sheet.getRow(0);
    int durationColumn = columnTitled(titles, "Dauer");
    int hoursColumn = columnTitled(titles, "Stunden");
    int budgetColumn = columnTitled(titles, "Budget");
    var sumRow = sheet.getRow(sheet.getLastRowNum());
    assertThat(sumRow.getCell(budgetColumn)).isNull();
    assertThat(sumRow.getCell(durationColumn).getNumericCellValue()).isEqualTo(120.0 / (24 * 60));
    assertThat(sumRow.getCell(hoursColumn).getNumericCellValue()).isEqualTo(2.0);
  }

  @Test
  void the_grand_total_stands_under_the_duration_column_without_target_hours() throws Exception {
    var options = InvoiceOptions.builder()
        .showTimereports(true).showEmployee(true).showTaskdescriptions(true)
        .build();

    var sheet = export(invoiceOfTwoHours(options), options);

    var titles = sheet.getRow(0);
    var sumRow = sheet.getRow(sheet.getLastRowNum());
    assertThat(sumRow.getCell(columnTitled(titles, "Dauer")).getNumericCellValue()).isEqualTo(120.0 / (24 * 60));
    assertThat(sumRow.getCell(columnTitled(titles, "Stunden")).getNumericCellValue()).isEqualTo(2.0);
  }

  /** Below the total, one row per kind and rate with the surcharge, then the total including it (#1470). */
  @Test
  void the_surcharges_stand_below_the_total_and_the_total_including_them_last() throws Exception {
    var options = InvoiceOptions.builder()
        .showTimereports(true).showEmployee(true).showTaskdescriptions(true)
        .build();

    var sheet = export(invoiceOfTwoHours(options, SurchargeKind.SUNDAY, 100), options);

    int durationColumn = columnTitled(sheet.getRow(0), "Dauer");
    int last = sheet.getLastRowNum();
    // total, heading, Sunday 100 %, total including the surcharges
    assertThat(sheet.getRow(last - 3).getCell(durationColumn).getNumericCellValue()).isEqualTo(120.0 / (24 * 60));
    assertThat(sheet.getRow(last - 2).getCell(0).getStringCellValue()).isEqualTo("Zeitzuschläge");
    assertThat(sheet.getRow(last - 1).getCell(0).getStringCellValue()).isEqualTo("Sonntag 100 % auf 2:00");
    assertThat(sheet.getRow(last - 1).getCell(durationColumn).getNumericCellValue()).isEqualTo(120.0 / (24 * 60));
    assertThat(sheet.getRow(last).getCell(durationColumn + 1).getNumericCellValue()).isEqualTo(4.0);
  }

  @Test
  void without_surcharges_the_total_is_the_last_row() throws Exception {
    var options = InvoiceOptions.builder().showTimereports(true).build();

    var sheet = export(invoiceOfTwoHours(options), options);

    assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(columnTitled(sheet.getRow(0), "Stunden")).getNumericCellValue())
        .isEqualTo(2.0);
    assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(0)).isNull();
  }

  /** A branch without bookings carries the subtotal of the suborders below it, which follow indented (#217). */
  @Test
  void a_branch_row_carries_the_subtotal_of_its_children() throws Exception {
    var options = InvoiceOptions.builder().showTimereports(true).build();

    var sheet = export(branchWithOneChild(options), options);

    int hoursColumn = columnTitled(sheet.getRow(0), "Stunden");
    // title, branch, child, its booking, total
    assertThat(sheet.getLastRowNum()).isEqualTo(4);
    assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).doesNotStartWith(" ");
    assertThat(sheet.getRow(1).getCell(hoursColumn).getNumericCellValue()).isEqualTo(2.0);
    assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).startsWith("  ");
    assertThat(sheet.getRow(2).getCell(hoursColumn).getNumericCellValue()).isEqualTo(2.0);
  }

  /** Deselecting a branch removes its row, not the suborders below it. */
  @Test
  void a_deselected_branch_leaves_its_children_in_the_export() throws Exception {
    var options = InvoiceOptions.builder().showTimereports(true).build();
    var data = branchWithOneChild(options);
    data.getSuborders().get(0).setVisible(false);

    var sheet = export(data, options);

    int hoursColumn = columnTitled(sheet.getRow(0), "Stunden");
    // title, child, its booking, total
    assertThat(sheet.getLastRowNum()).isEqualTo(3);
    assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).startsWith("  ");
    assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(hoursColumn).getNumericCellValue()).isEqualTo(2.0);
  }

  // --- fixture ---------------------------------------------------------------------------------

  /** A branch without bookings, and below it one suborder with a booking of two hours. */
  private static InvoiceData branchWithOneChild(InvoiceOptions options) {
    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setCustomer(new Customer());
    setId(customerorder, 1L);
    var branch = new Suborder();
    setId(branch, 10L);
    branch.setSign("01");
    branch.setCustomerorder(customerorder);
    var child = new Suborder();
    setId(child, 11L);
    child.setSign("01");
    child.setCustomerorder(customerorder);
    child.setParentorder(branch);

    var booking = new InvoiceTimereport(TimereportDTO.builder()
        .id(110L).suborderId(11L).employeeName("Person A").employeeSign("pa")
        .referenceday(JAN).duration(Duration.ofHours(2)).taskdescription("work")
        .build());
    var invoiceBranch = new InvoiceSuborder(branch, List.of(), options);
    var invoiceChild = new InvoiceSuborder(child, List.of(booking), options);
    var branchGroup = InvoiceGroup.ofSuborder(invoiceBranch);
    var childGroup = InvoiceGroup.ofSuborder(invoiceChild);
    childGroup.setDepth(1);
    childGroup.getLines().add(InvoiceLine.whole(booking, invoiceChild));
    branchGroup.getChildren().add(childGroup);

    return new InvoiceData(options, new LocalDateRange(JAN, JAN), "co", customerorder.getCustomer(),
        Duration.ofHours(2), List.of(invoiceBranch, invoiceChild), InvoiceGrouping.SUBORDER, List.of(branchGroup),
        List.of(), List.of());
  }

  private static Sheet export(InvoiceData data, InvoiceOptions options) throws Exception {
    var source = new StaticMessageSource();
    source.addMessage("main.invoice.ticket.none", Locale.GERMAN, "ohne Ticket");
    source.addMessage("main.invoice.surcharges.text", Locale.GERMAN, "Zeitzuschläge");
    source.addMessage("main.invoice.overall.withsurcharges.text", Locale.GERMAN, "Gesamtsumme inkl. Zuschläge");
    source.addMessage("main.surcharge.flag", Locale.GERMAN, "{0} {1}");
    source.addMessage("main.surcharge.percent", Locale.GERMAN, "{0} %");
    source.addMessage("main.surcharge.kind.sunday", Locale.GERMAN, "Sonntag");
    source.addMessage("main.invoice.surcharge.line", Locale.GERMAN, "{0} auf {1}");
    var messages = new MessageSourceAccessor(source, Locale.GERMAN);
    var bytes = new ExcelExportService(messages).exportToExcel(data, options, headers());
    return new XSSFWorkbook(new ByteArrayInputStream(bytes)).getSheetAt(0);
  }

  private static int columnTitled(Row titles, String title) {
    for (var cell : titles) {
      if (cell.getCellType() == CellType.STRING && title.equals(cell.getStringCellValue())) {
        return cell.getColumnIndex();
      }
    }
    throw new AssertionError("no column titled " + title);
  }

  private static InvoiceData invoiceOfTwoHours(InvoiceOptions options) {
    return invoiceOfTwoHours(options, null, null);
  }

  private static InvoiceData invoiceOfTwoHours(InvoiceOptions options, SurchargeKind kind, Integer percent) {
    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setCustomer(new Customer());
    setId(customerorder, 1L);
    var suborder = new Suborder();
    setId(suborder, 10L);
    suborder.setSign("01");
    suborder.setCustomerorder(customerorder);
    suborder.setDebithours(Duration.ofHours(8));

    var booking = new InvoiceTimereport(TimereportDTO.builder()
        .id(100L).suborderId(10L).employeeName("Person A").employeeSign("pa")
        .referenceday(JAN).duration(Duration.ofHours(2)).taskdescription("work")
        .surchargeKind(kind).surchargePercent(percent)
        .surchargeCredit(kind == null ? Duration.ZERO : Timereport.surchargeCreditFor(Duration.ofHours(2), percent))
        .build());
    var invoiceSuborder = new InvoiceSuborder(suborder, List.of(booking), options);
    var group = InvoiceGroup.ofSuborder(invoiceSuborder);
    group.getLines().add(InvoiceLine.whole(booking, invoiceSuborder));

    return new InvoiceData(options, new LocalDateRange(JAN, JAN), "co", customerorder.getCustomer(),
        Duration.ofHours(2), List.of(invoiceSuborder), InvoiceGrouping.SUBORDER, List.of(group), List.of(), List.of());
  }

  private static InvoiceColumnHeaders headers() {
    return new InvoiceColumnHeaders() {
      @Override public String getOrderHeader() { return "Auftrag"; }
      @Override public String getDateHeader() { return "Datum"; }
      @Override public String getEmployeeHeader() { return "Mitarbeiter"; }
      @Override public String getTaskDescriptionHeader() { return "Kommentar"; }
      @Override public String getTicketHeader() { return "Ticket"; }
      @Override public String getBudgetHeader() { return "Budget"; }
      @Override public String getDurationHeader() { return "Dauer"; }
      @Override public String getHoursHeader() { return "Stunden"; }
    };
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
