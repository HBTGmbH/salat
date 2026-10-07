package de.hbt.salat.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;
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
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.common.viewhelper.NoticeViewHelper;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SpecialOrders;
import de.hbt.salat.order.service.SuborderService;

/**
 * Eine Unterauftragsnummer aus der Anfrage, zu der es keinen Unterauftrag gibt, beantwortet jeder Handler gleich:
 * Umleitung auf die Liste der Unteraufträge mit der Meldung zu {@code SO-0004}, kein Serverfehler (#1401). Nennt die
 * Anfrage nur den Auftrag, unter dem das Formular steht, und gibt es ihn nicht, bleibt das Formular offen, ohne ihn.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class SuborderControllerNotFoundTest {

  private static final long UNKNOWN_ID = 999L;
  private static final long CUSTOMER_ID = 3L;
  private static final long ORDER_ID = 7L;
  private static final String LIST = "/orders/suborders";
  private static final String NOT_FOUND_MESSAGE = "Der Unterauftrag wurde nicht gefunden.";

  @Mock private SuborderService suborderService;
  @Mock private CustomerorderService customerorderService;
  @Mock private CustomerService customerService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new SuborderController(suborderService, customerorderService, customerService, messages,
        new ErrorCodeViewHelper(messages), mock(FilterHintViewHelper.class), mock(NoticeViewHelper.class),
        mock(SpecialOrders.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    var customer = new Customer();
    setField(customer, "id", CUSTOMER_ID);
    var order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setCustomer(customer);
    order.setSign("co");
    order.setFromDate(LocalDate.parse("2026-01-01"));

    when(customerService.getSelectableCustomers(any())).thenReturn(List.of(customer));
    when(customerorderService.getVisibleCustomerorders()).thenReturn(List.of(order));
    when(customerorderService.getCustomerorderById(ORDER_ID)).thenReturn(order);
    when(customerorderService.getCustomerorderById(UNKNOWN_ID)).thenReturn(null);
    when(suborderService.getSuborderById(UNKNOWN_ID)).thenReturn(null);
  }

  @Test
  void edit_form_leads_back_to_the_list() throws Exception {
    assertLeadsBackToTheListWithNotFound(mockMvc.perform(get("/orders/suborders/{id}/edit", UNKNOWN_ID)));
  }

  @Test
  void storing_changes_leads_back_to_the_list_without_updating() throws Exception {
    var request = post("/orders/suborders/store")
        .param("id", String.valueOf(UNKNOWN_ID))
        .param("sign", "01");

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(request));
    verify(suborderService, never()).update(anyLong(), any(), any());
  }

  @Test
  void toggling_hide_leads_back_to_the_list() throws Exception {
    when(suborderService.toggleHide(UNKNOWN_ID)).thenThrow(new InvalidDataException(ErrorCode.SO_NOT_FOUND));

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/orders/suborders/{id}/toggle-hide", UNKNOWN_ID)));
  }

  @Test
  void deleting_leads_back_to_the_list() throws Exception {
    doThrow(new InvalidDataException(ErrorCode.SO_NOT_FOUND)).when(suborderService).deleteSuborderById(UNKNOWN_ID);

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/orders/suborders/{id}/delete", UNKNOWN_ID)));
  }

  @Test
  void copying_leads_back_to_the_list() throws Exception {
    doThrow(new InvalidDataException(ErrorCode.SO_NOT_FOUND)).when(suborderService).createCopy(UNKNOWN_ID);

    assertLeadsBackToTheListWithNotFound(mockMvc.perform(post("/orders/suborders/{id}/copy", UNKNOWN_ID)));
  }

  @Test
  void a_new_form_for_an_unknown_order_opens_without_it() throws Exception {
    var result = mockMvc.perform(get("/orders/suborders/create").param("customerorderId", String.valueOf(UNKNOWN_ID)))
        .andExpect(status().isOk())
        .andExpect(view().name("order/sub-order-form"))
        .andReturn();

    var form = (SuborderForm) result.getModelAndView().getModel().get("suborderForm");
    assertThat(form.getCustomerorderId()).isEqualTo(ORDER_ID);
  }

  @Test
  void choosing_an_unknown_order_in_the_form_keeps_the_form_open() throws Exception {
    mockMvc.perform(post("/orders/suborders/change-customerorder")
            .param("customerId", String.valueOf(CUSTOMER_ID))
            .param("customerorderId", String.valueOf(UNKNOWN_ID)))
        .andExpect(status().isOk())
        .andExpect(view().name("order/sub-order-form"));
  }

  private static void assertLeadsBackToTheListWithNotFound(ResultActions result) throws Exception {
    result.andExpect(redirectedUrl(LIST))
        .andExpect(flash().attribute("toastError", NOT_FOUND_MESSAGE))
        .andExpect(flash().attributeCount(1));
  }
}
