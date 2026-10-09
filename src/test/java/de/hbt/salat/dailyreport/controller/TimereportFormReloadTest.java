package de.hbt.salat.dailyreport.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Neu laden auf der Adresse, an die das Buchungsformular sendet (#1444). Mit Ticket-Vorschlägen oder einem
 * Validierungsfehler antwortet das Speichern mit dem Formular selbst, und in der Adresszeile bleibt die POST-Adresse
 * stehen. Neu laden, ein wiederhergestellter Tab oder Zurück fragen sie per GET ab; das führt zurück ins Formular statt
 * auf die Fehlerseite. Die nicht gespeicherten Eingaben sind dabei verloren.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportFormReloadTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long UNKNOWN_ID = 999L;

  @Mock private TimereportService timereportService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private WorkingdayService workingdayService;
  @Mock private DailyService dailyService;
  @Mock private FavoriteService favoriteService;
  @Mock private EmployeeService employeeService;
  @Mock private MessageSourceAccessor messages;
  @Mock private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock private DailyPreferenceService dailyPreferenceService;
  @Mock private TimereportPreferenceService timereportPreferenceService;
  @Mock private NotificationService notificationService;
  @Mock private AuthorizedUser authorizedUser;
  @Mock private AuthorizedEmployee authorizedEmployee;

  @InjectMocks private TimereportController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void reloading_a_new_booking_leads_to_the_empty_form() throws Exception {
    mockMvc.perform(get("/dailyreport/timereports"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/dailyreport/timereports/new"));
  }

  @Test
  void reloading_an_edited_booking_leads_to_its_edit_form() throws Exception {
    mockMvc.perform(get("/dailyreport/timereports/{id}", TIMEREPORT_ID))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/dailyreport/timereports/3/edit"));
  }

  @Test
  void reloading_an_unknown_booking_ends_where_its_edit_form_does() throws Exception {
    when(timereportService.getTimereportById(UNKNOWN_ID)).thenReturn(null);

    mockMvc.perform(get("/dailyreport/timereports/{id}", UNKNOWN_ID))
        .andExpect(redirectedUrl("/dailyreport/timereports/999/edit"));
    mockMvc.perform(get("/dailyreport/timereports/{id}/edit", UNKNOWN_ID))
        .andExpect(redirectedUrl("/dailyreport/daily"));
  }

}
