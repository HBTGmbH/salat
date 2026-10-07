package de.hbt.salat.employee.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.util.ReflectionTestUtils.setField;
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
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;

/**
 * Eine Mitarbeiternummer aus der Anfrage, zu der es keinen Mitarbeiter gibt, beantwortet jeder Handler gleich:
 * Umleitung auf die Mitarbeiterliste mit der Meldung zu {@code EM-0003}, kein Serverfehler (#1401).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeControllerNotFoundTest {

  private static final long UNKNOWN_ID = 999L;
  private static final long LOGIN_ID = 1L;
  private static final String LIST = "/employees";
  private static final String NOT_FOUND_MESSAGE = "Der Mitarbeiter wurde nicht gefunden.";

  @Mock private EmployeeService employeeService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new EmployeeController(employeeService, messages, new ErrorCodeViewHelper(messages),
        mock(FilterHintViewHelper.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    var loginEmployee = new Employee();
    setField(loginEmployee, "id", LOGIN_ID);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeeService.getEmployeeById(UNKNOWN_ID)).thenReturn(null);
    when(employeeService.getEmployeeForView(UNKNOWN_ID)).thenReturn(null);
  }

  @Test
  void view_leads_back_to_the_list() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(get("/employees/view").param("id", String.valueOf(UNKNOWN_ID))));
  }

  @Test
  void edit_form_leads_back_to_the_list() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(get("/employees/edit").param("id", String.valueOf(UNKNOWN_ID))));
  }

  @Test
  void storing_changes_leads_back_to_the_list_without_saving() throws Exception {
    var request = post("/employees/store")
        .param("id", String.valueOf(UNKNOWN_ID))
        .param("sign", "xyz");

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(request));
    verify(employeeService, never()).createOrUpdate(any());
  }

  @Test
  void toggling_hide_leads_back_to_the_list() throws Exception {
    when(employeeService.toggleHide(UNKNOWN_ID)).thenThrow(new InvalidDataException(ErrorCode.EM_NOT_FOUND));

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/employees/{id}/toggle-hide", UNKNOWN_ID)));
  }

  @Test
  void anonymizing_leads_back_to_the_list_without_anonymizing() throws Exception {
    var request = post("/employees/{id}/anonymize", UNKNOWN_ID).param("confirmSign", "xyz");

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(request));
    verify(employeeService, never()).anonymizeEmployee(anyLong(), anyString());
  }

  @Test
  void deleting_leads_back_to_the_list() throws Exception {
    doThrow(new InvalidDataException(ErrorCode.EM_NOT_FOUND)).when(employeeService).deleteEmployeeById(UNKNOWN_ID);

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/employees/{id}/delete", UNKNOWN_ID)));
  }

  private static void assertLeadsBackToTheListWithNotFound(ResultActions result) throws Exception {
    result.andExpect(redirectedUrl(LIST))
        .andExpect(flash().attribute("toastError", NOT_FOUND_MESSAGE))
        .andExpect(flash().attributeCount(1));
  }
}
