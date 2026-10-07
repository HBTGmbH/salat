package de.hbt.salat.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
import de.hbt.salat.common.viewhelper.NoticeViewHelper;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SpecialOrders;

/**
 * Eine Auftragsnummer aus der Anfrage, zu der es keinen Auftrag gibt, beantwortet jeder Handler gleich: Umleitung auf
 * die Auftragsliste mit der Meldung zu {@code CO-0005}, kein Serverfehler (#1401).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerorderControllerNotFoundTest {

  private static final long UNKNOWN_ID = 999L;
  private static final String LIST = "/orders/customerorders";
  private static final String NOT_FOUND_MESSAGE = "Der Auftrag wurde nicht gefunden.";

  @Mock private CustomerorderService customerorderService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new CustomerorderController(customerorderService, mock(CustomerService.class),
        mock(EmployeeService.class), messages, new ErrorCodeViewHelper(messages), mock(AuthorizedEmployee.class),
        mock(FilterHintViewHelper.class), mock(NoticeViewHelper.class), mock(SpecialOrders.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    when(customerorderService.getCustomerorderById(UNKNOWN_ID)).thenReturn(null);
  }

  @Test
  void edit_form_leads_back_to_the_list() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(get("/orders/customerorders/edit").param("id", String.valueOf(UNKNOWN_ID))));
  }

  @Test
  void storing_changes_leads_back_to_the_list_without_updating() throws Exception {
    var request = post("/orders/customerorders/store")
        .param("id", String.valueOf(UNKNOWN_ID))
        .param("sign", "A1");

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(request));
    verify(customerorderService, never()).update(anyLong(), any());
  }

  @Test
  void toggling_hide_leads_back_to_the_list() throws Exception {
    when(customerorderService.toggleHide(UNKNOWN_ID)).thenThrow(new InvalidDataException(ErrorCode.CO_NOT_FOUND));

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/orders/customerorders/{id}/toggle-hide", UNKNOWN_ID)));
  }

  @Test
  void deleting_leads_back_to_the_list() throws Exception {
    doThrow(new InvalidDataException(ErrorCode.CO_NOT_FOUND))
        .when(customerorderService).deleteCustomerorderById(UNKNOWN_ID);

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/orders/customerorders/{id}/delete", UNKNOWN_ID)));
  }

  private static void assertLeadsBackToTheListWithNotFound(ResultActions result) throws Exception {
    result.andExpect(redirectedUrl(LIST))
        .andExpect(flash().attribute("toastError", NOT_FOUND_MESSAGE))
        .andExpect(flash().attributeCount(1));
  }
}
