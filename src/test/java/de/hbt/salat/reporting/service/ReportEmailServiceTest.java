package de.hbt.salat.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.mail.MailTestTemplates;
import de.hbt.salat.common.service.MailService;
import de.hbt.salat.common.service.MailService.EmailRequest;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportParameter;
import de.hbt.salat.reporting.domain.ReportResult;
import de.hbt.salat.reporting.domain.ReportResultRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** A report from the schedule comes in the common layout of all mails, in the du-form (#1505). */
@FixedClock("2026-10-01T05:00:00")
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportEmailServiceTest {

  private static final long REPORT_ID = 3L;

  private final ExcelExportService excelExportService = mock(ExcelExportService.class);
  private final ReportService reportService = mock(ReportService.class);
  private final MailService mailService = mock(MailService.class);
  private ReportEmailService service;

  @BeforeEach
  void setUp() throws Exception {
    var messageSource = MailTestTemplates.messageSource();
    var properties = new SalatProperties();
    properties.setUrl("https://salat.example.org");
    service = new ReportEmailService(excelExportService, reportService, mailService,
        MailTestTemplates.mailTemplates(messageSource), messageSource, properties);
    ReflectionTestUtils.setField(service, "fromAddress", "noreply@example.org");
    ReflectionTestUtils.setField(service, "emailEnabled", true);

    var definition = new ReportDefinition();
    definition.setName("Stunden je Auftrag");
    when(reportService.getReportDefinition(REPORT_ID)).thenReturn(definition);
    when(reportService.execute(any(), any())).thenReturn(ReportResult.builder()
        .rows(List.of(new ReportResultRow(), new ReportResultRow())).build());
    when(excelExportService.exportToExcel(any())).thenReturn(new byte[] {1});
  }

  @Test
  void comes_from_salat_with_the_report_in_the_subject() {
    var mail = send();

    assertThat(mail.getSubject()).isEqualTo("SALAT: Report Stunden je Auftrag");
    assertThat(mail.getFrom().getName()).isEqualTo("Salat");
    assertThat(mail.getFrom().getEmail()).isEqualTo("noreply@example.org");
    assertThat(mail.getAttachments()).singleElement()
        .satisfies(attachment -> assertThat(attachment.getFileName()).endsWith(".xlsx"));
  }

  @Test
  void the_text_part_lists_parameters_rows_and_time_in_the_du_form() {
    assertThat(send().getMessage()).isEqualTo("""
        Hallo,

        anbei der Report „Stunden je Auftrag“.

        - Zeitraum: 2026-09
        - Zeilen: 2
        - Erzeugt am: 01.10.2026 05:00

        Die Ergebnisse stehen im angehängten Excel-Dokument.

        __________________________

        Diese E-Mail hat Salat automatisch verschickt.
        Den Zeitplan dieses Reports pflegst du unter Reports.
        https://salat.example.org/reporting/jobs""");
  }

  @Test
  void the_html_part_has_the_salat_layout_with_the_attachment_named() {
    var html = send().getHtmlMessage();

    assertThat(html)
        .contains("src=\"cid:salat-logo\"")
        .contains(">Report „Stunden je Auftrag“<")
        .contains(">Zeitraum<", ">2026-09<", ">Zeilen<", ">2<", ">01.10.2026 05:00<")
        .contains("report-Stunden_je_Auftrag-erzeugt-01-10-26-0500.xlsx")
        .contains("href=\"https://salat.example.org/reporting/jobs\"");
  }

  private EmailRequest send() {
    service.sendReportEmail(REPORT_ID,
        List.of(ReportParameter.builder().name("Zeitraum").type("string").value("2026-09").build()),
        new String[] {"person.b@example.org"}, false);
    var request = ArgumentCaptor.forClass(EmailRequest.class);
    verify(mailService).sendEmail(request.capture());
    return request.getValue();
  }
}
