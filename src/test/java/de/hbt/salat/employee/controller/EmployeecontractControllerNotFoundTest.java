package de.hbt.salat.employee.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * Eine Vertragsnummer aus der Anfrage, zu der es keinen Vertrag gibt — ein veralteter Link, ein Lesezeichen, eine von
 * Hand geänderte Adresse —, beantwortet jeder Handler gleich: Umleitung auf die Liste mit der Meldung zu
 * {@code EC-0006}, kein Serverfehler (#1401).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeecontractControllerNotFoundTest {

  private static final long UNKNOWN_ID = 999L;
  private static final String LIST = "/employees/contracts";
  private static final String NOT_FOUND_MESSAGE = "Der Mitarbeitervertrag wurde nicht gefunden.";

  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new EmployeecontractController(employeecontractService, employeeService, messages,
        new ErrorCodeViewHelper(messages), mock(FilterHintViewHelper.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    when(employeecontractService.getEmployeecontractById(UNKNOWN_ID)).thenReturn(null);
    when(employeecontractService.getEmployeecontractForView(UNKNOWN_ID)).thenReturn(null);
  }

  @Test
  void edit_form_leads_back_to_the_list() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(get("/employees/contracts/edit").param("id", String.valueOf(UNKNOWN_ID))));
  }

  @Test
  void view_leads_back_to_the_list() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(get("/employees/contracts/view").param("id", String.valueOf(UNKNOWN_ID))));
  }

  @Test
  void storing_changes_leads_back_to_the_list_without_updating() throws Exception {
    var request = post("/employees/contracts/store")
        .param("id", String.valueOf(UNKNOWN_ID))
        .param("validFrom", "2026-01-01")
        .param("dailyWorkingTime", "8:00")
        .param("yearlyVacation", "30");

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(request));
    verify(employeecontractService, never()).updateEmployeecontract(anyLong(), any(), any(), anyList(), anyString(),
        anyBoolean(), anyBoolean(), any(), anyInt(), anyBoolean());
  }

  @Test
  void adding_overtime_leads_back_to_the_list_without_creating_it() throws Exception {
    var request = post("/employees/contracts/{id}/overtime", UNKNOWN_ID)
        .param("newOvertimeComment", "Korrektur")
        .param("newOvertime", "1:00");

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(request));
    verify(employeecontractService, never()).create(any());
  }

  @Test
  void deleting_leads_back_to_the_list_without_reporting_success() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/employees/contracts/{id}/delete", UNKNOWN_ID)));
    verify(employeecontractService, never()).deleteEmployeeContractById(anyLong());
  }

  @Test
  void toggling_hide_leads_back_to_the_list() throws Exception {
    when(employeecontractService.toggleHide(UNKNOWN_ID))
        .thenThrow(new InvalidDataException(ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND));

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/employees/contracts/{id}/toggle-hide", UNKNOWN_ID)));
  }

  private static void assertLeadsBackToTheListWithNotFound(ResultActions result) throws Exception {
    result.andExpect(redirectedUrl(LIST))
        .andExpect(flash().attribute("toastError", NOT_FOUND_MESSAGE))
        .andExpect(flash().attributeCount(1));
  }
}
