package de.hbt.salat.order.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Base64;
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
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.filter.UiStateFilter;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.common.viewhelper.NoticeViewHelper;
import de.hbt.salat.common.web.ListLimits;
import de.hbt.salat.common.web.LoginSignProvider;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.common.web.UiStateKeyRegistry;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.SearchHits;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SpecialOrders;
import de.hbt.salat.order.service.SuborderService;

/**
 * The order, suborder and employee order lists search on every call, without a filter too, and ask for at most as many
 * rows as the limit says (#1380). The limit is remembered per list, like every other filter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderListLimitTest {

  private static final String LOGIN = "tst";

  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private CustomerService customerService;
  @Mock private LoginSignProvider loginSignProvider;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messages = new MessageSourceAccessor(new StaticMessageSource(), Locale.GERMANY);
    var listLimits = new ListLimits(new SalatProperties());
    var customerorders = new CustomerorderController(customerorderService, customerService, mock(EmployeeService.class),
        messages, new ErrorCodeViewHelper(messages), mock(AuthorizedEmployee.class), mock(FilterHintViewHelper.class),
        mock(NoticeViewHelper.class), mock(SpecialOrders.class), listLimits);
    var suborders = new SuborderController(suborderService, customerorderService, customerService, messages,
        new ErrorCodeViewHelper(messages), mock(FilterHintViewHelper.class), mock(NoticeViewHelper.class),
        mock(SpecialOrders.class), listLimits);
    var employeeorders = new EmployeeorderController(employeeorderService, customerService, employeecontractService,
        customerorderService, suborderService, messages, new ErrorCodeViewHelper(messages),
        mock(FilterHintViewHelper.class), listLimits);
    // UiState lebt je Anfrage; der Filter liest den gemerkten Stand aus dem Cookie.
    var uiStateFilter = new UiStateFilter(new UiState(),
        new UiStateKeyRegistry(List.of(new OrderUiStateKeyContributor())), loginSignProvider, new SalatProperties());
    mockMvc = MockMvcBuilders.standaloneSetup(customerorders, suborders, employeeorders)
        .addFilters(uiStateFilter)
        .build();

    when(loginSignProvider.getEffectiveLoginSign()).thenReturn(LOGIN);
    when(customerorderService.searchCustomerorders(any(), any(), any(), any(), anyInt())).thenReturn(SearchHits.none());
    when(suborderService.searchSuborders(any(), any(), any(), any(), any(), anyInt())).thenReturn(SearchHits.none());
    when(employeeorderService.searchEmployeeorderListItems(any(), any(), any(), any(), any(), any(), anyBoolean(),
        any(), anyInt())).thenReturn(SearchHits.none());
  }

  @Test
  void the_order_list_searches_without_a_filter_up_to_the_default_limit() throws Exception {
    perform(get("/orders/customerorders"));

    verify(customerorderService).searchCustomerorders(isNull(), isNull(), isNull(), isNull(), eq(500));
  }

  @Test
  void the_suborder_list_searches_without_a_filter_up_to_the_default_limit() throws Exception {
    perform(get("/orders/suborders"));

    verify(suborderService).searchSuborders(isNull(), isNull(), isNull(), isNull(), isNull(), eq(500));
  }

  @Test
  void the_employee_order_list_searches_without_a_filter_up_to_the_default_limit() throws Exception {
    perform(get("/orders/employeeorders"));

    verify(employeeorderService).searchEmployeeorderListItems(isNull(), isNull(), isNull(), isNull(), isNull(),
        isNull(), eq(false), isNull(), eq(500));
  }

  @Test
  void the_chosen_limit_is_what_the_query_asks_for_and_what_the_select_shows() throws Exception {
    perform(get("/orders/suborders").param("fSuborderLimit", "50"))
        .andExpect(model().attribute("limit", 50))
        .andExpect(model().attribute("limits", ListLimits.STEPS));

    verify(suborderService).searchSuborders(isNull(), isNull(), isNull(), isNull(), isNull(), eq(50));
  }

  @Test
  void all_asks_for_at_most_the_configured_number_of_rows() throws Exception {
    perform(get("/orders/customerorders").param("fCustomerOrderLimit", "0"))
        .andExpect(model().attribute("limit", 0));

    verify(customerorderService).searchCustomerorders(isNull(), isNull(), isNull(), isNull(), eq(10_000));
  }

  /** Each list remembers its own limit (ADR-0022): a page call without parameters uses what was chosen before. */
  @Test
  void each_list_remembers_its_own_limit() throws Exception {
    var remembered = remembered("customerOrder.Limit=50&suborder.Limit=100&employeeOrder.Limit=1000");

    perform(get("/orders/customerorders").cookie(remembered));
    perform(get("/orders/suborders").cookie(remembered));
    perform(get("/orders/employeeorders").cookie(remembered));

    verify(customerorderService).searchCustomerorders(isNull(), isNull(), isNull(), isNull(), eq(50));
    verify(suborderService).searchSuborders(isNull(), isNull(), isNull(), isNull(), isNull(), eq(100));
    verify(employeeorderService).searchEmployeeorderListItems(isNull(), isNull(), isNull(), isNull(), isNull(),
        isNull(), eq(false), isNull(), eq(1000));
  }

  private ResultActions perform(MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(request).andExpect(status().isOk());
  }

  /** The cookie an earlier visit left behind: the values under their key names, bound to the login. */
  private static Cookie remembered(String values) {
    var value = "_ls=" + LOGIN + "&" + values;
    return new Cookie("salat_uistate", Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(UTF_8)));
  }
}
