package de.hbt.salat.dailyreport.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverter;
import de.hbt.salat.dailyreport.service.BookingOrderResolver;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.ImportReport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;

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
  private BookingOrderResolver bookingOrderResolver;
  @Mock
  private AuthorizedUser authorizedUser;

  private MockMvc mockMvc;
  private Employee employee;

  @BeforeEach
  void setUp() {
    employee = new Employee();
    ReflectionTestUtils.setField(employee, "id", 42L);
    employee.setSign("testuser");
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", 7L);
    contract.setEmployee(employee);
    when(employeecontractService.getEmployeecontractById(7L)).thenReturn(contract);

    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new DailyReportCsvController(
        new DailyWorkingReportCsvConverter(bookingOrderResolver, authorizedUser),
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

  /* A ticket reference longer than a booking can store is named with line and column, like an
     unreadable value, and the import saves nothing (#1140). */
  @Test
  void names_line_and_column_of_a_too_long_ticket_reference() throws Exception {
    var csv = """
        date,type,startTime,breakTime,employeeorderId,workingTime,comment,ticketReference
        2024-11-04,WORKED,09:00,00:30,183209,00:30,Team-Mittag,%s
        """.formatted("X".repeat(65));

    var result = mockMvc.perform(multipart("/dailyreport/csv/import")
            .file(new MockMultipartFile("file", "report.csv", "text/csv", csv.getBytes(UTF_8)))
            .param("fEmployeeContractId", "7"))
        .andExpect(status().is3xxRedirection())
        .andReturn();

    verifyNoInteractions(dailyWorkingReportService);
    assertThat(result.getFlashMap().get("toastError").toString())
        .contains("Zeile 2", "ticketReference", "64 Zeichen", "nichts gespeichert");
  }

  /* A booking that cannot be assigned is named with its line, the reason, and that nothing was saved (#1142). */
  @Test
  void names_the_line_of_a_booking_without_a_matching_order() throws Exception {
    var csv = """
        date,type,startTime,breakTime,suborderSign,workingTime,comment
        2024-11-04,WORKED,09:00,00:30,111/1,00:30,Team-Mittag
        """;
    when(bookingOrderResolver.resolveFor(eq(employee), any(), eq(LocalDate.of(2024, 11, 4))))
        .thenThrow(new InvalidDataException(TR_BOOKING_NO_EMPLOYEE_ORDER, "111/1", "testuser", "2024-11-04"));

    var result = mockMvc.perform(multipart("/dailyreport/csv/import")
            .file(new MockMultipartFile("file", "report.csv", "text/csv", csv.getBytes(UTF_8)))
            .param("fEmployeeContractId", "7"))
        .andExpect(status().is3xxRedirection())
        .andReturn();

    verifyNoInteractions(dailyWorkingReportService);
    assertThat(result.getFlashMap().get("toastError").toString())
        .isEqualTo("Zeile 2: Für testuser gibt es am 2024-11-04 keinen gültigen Mitarbeiterauftrag zum Unterauftrag „111/1“. "
            + "Das Kürzel wird vollständig und exakt verglichen, etwa 4711/01. Es wurde nichts gespeichert.");
  }

  /* The file goes to the employee of the selected contract, whoever is logged in (#1142). */
  @Test
  void imports_for_the_employee_of_the_selected_contract() throws Exception {
    var csv = """
        date,type,startTime,breakTime
        2024-11-04,NOT_WORKED,,
        """;
    when(dailyWorkingReportService.updateReports(any(), eq(employee))).thenReturn(new ImportReport(List.of()));

    mockMvc.perform(multipart("/dailyreport/csv/import")
            .file(new MockMultipartFile("file", "report.csv", "text/csv", csv.getBytes(UTF_8)))
            .param("importMode", "replace")
            .param("fEmployeeContractId", "7"))
        .andExpect(status().is3xxRedirection());

    verify(dailyWorkingReportService).updateReports(any(), eq(employee));
  }

  private static MockMultipartFile csvFile() {
    return new MockMultipartFile("file", "report.csv", "text/csv",
        CSV_WITH_UNREADABLE_TIME.getBytes(UTF_8));
  }
}
