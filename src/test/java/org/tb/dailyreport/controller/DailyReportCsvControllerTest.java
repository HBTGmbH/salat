package org.tb.dailyreport.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.rest.DailyWorkingReportCsvConverter;
import org.tb.dailyreport.service.DailyWorkingReportService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

/**
 * Der Ausgang, an dem #1112 hing: ein Uhrzeitfeld der hochgeladenen Datei passte in keines der
 * erwarteten Formate, und der Import endete mit HTTP 500 auf der allgemeinen Fehlerseite. Der Test
 * fährt den echten Konverter, damit die Umwandlung in einen Eingabefehler mitgeprüft wird.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class DailyReportCsvControllerTest {

  private static final String CSV_WITH_UNREADABLE_TIME = """
      date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment
      2024-11-04,WORKED,9 Uhr,00:30,183209,111,Rumsitzen,111/01,Stuhlpolsterung,00:30,Team-Mittag
      """;

  @Mock
  private DailyWorkingReportService dailyWorkingReportService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthorizedEmployee authorizedEmployee;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("org/tb/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new DailyReportCsvController(
        new DailyWorkingReportCsvConverter(employeeorderService, authorizedUser, authorizedEmployee),
        dailyWorkingReportService,
        employeecontractService,
        employeeService,
        messages,
        new ErrorCodeViewHelper(messages));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /* Die Antwort ist die Weiterleitung auf das Formular mit einer Meldung, nicht die Fehlerseite. */
  @Test
  void answers_an_unreadable_value_with_a_message_on_the_form() throws Exception {
    var result = mockMvc.perform(multipart("/dailyreport/csv/import")
            .file(csvFile())
            .param("fEmployeeContractId", "7"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/dailyreport/csv"))
        .andExpect(flash().attributeExists("toastError"))
        .andReturn();

    assertThat(result.getFlashMap().get("toastError").toString())
        .contains("Zeile 2", "startTime", "9 Uhr", "HH:mm, HH:mm:ss");
  }

  /* Die Meldung von opencsv hängt die vollständige Rohzeile an. Nichts davon darf die hochladende
     Person zu sehen bekommen - Zeilennummer, Spalte und der einzelne Wert genügen (#1112). */
  @Test
  void keeps_the_faulty_line_out_of_the_message() throws Exception {
    var result = mockMvc.perform(multipart("/dailyreport/csv/import")
            .file(csvFile())
            .param("fEmployeeContractId", "7"))
        .andReturn();

    assertThat(result.getFlashMap().get("toastError").toString())
        .doesNotContain("Team-Mittag", "Stuhlpolsterung", "Rumsitzen", "183209");
  }

  /* Eine fehlerhafte Zeile lässt die ganze Datei ungespeichert, und die Meldung sagt das. */
  @Test
  void saves_nothing_when_a_line_cannot_be_read() throws Exception {
    var result = mockMvc.perform(multipart("/dailyreport/csv/import")
            .file(csvFile())
            .param("fEmployeeContractId", "7"))
        .andReturn();

    verifyNoInteractions(dailyWorkingReportService);
    assertThat(result.getFlashMap().get("toastError").toString()).contains("nichts gespeichert");
  }

  private static MockMultipartFile csvFile() {
    return new MockMultipartFile("file", "report.csv", "text/csv",
        CSV_WITH_UNREADABLE_TIME.getBytes(UTF_8));
  }
}
