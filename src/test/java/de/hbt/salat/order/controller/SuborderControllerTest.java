package de.hbt.salat.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.common.viewhelper.NoticeViewHelper;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * The parent field of the suborder form (#1243): {@code parentId} is empty for the top level and
 * otherwise names a suborder. The sign proposal, the prefilled validity and the date check read it
 * the same way the service does — also when the parent suborder happens to carry the id of its
 * customer order, which is what {@link #TWIN} does here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class SuborderControllerTest {

  private static final long CUSTOMER_ID = 3L;
  private static final long ORDER_ID = 7L;
  /** A top-level suborder of the order that carries the order's id as its own. */
  private static final long TWIN = ORDER_ID;
  private static final LocalDate ORDER_FROM = LocalDate.parse("2026-01-01");
  private static final LocalDate TWIN_FROM = LocalDate.parse("2026-03-01");

  @Mock private SuborderService suborderService;
  @Mock private CustomerorderService customerorderService;
  @Mock private CustomerService customerService;

  private MockMvc mockMvc;
  private Customerorder order;
  private Suborder twin;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new SuborderController(suborderService, customerorderService, customerService, messages,
        new ErrorCodeViewHelper(messages), mock(FilterHintViewHelper.class), mock(NoticeViewHelper.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    var customer = new Customer();
    setField(customer, "id", CUSTOMER_ID);
    order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setCustomer(customer);
    order.setSign("co");
    order.setFromDate(ORDER_FROM);
    twin = new Suborder();
    setField(twin, "id", TWIN);
    twin.setCustomerorder(order);
    twin.setSign("01");
    twin.setFromDate(TWIN_FROM);

    when(customerService.getSelectableCustomers(any())).thenReturn(List.of(customer));
    when(customerorderService.getVisibleCustomerorders()).thenReturn(List.of(order));
    when(customerorderService.getCustomerorderById(ORDER_ID)).thenReturn(order);
    when(suborderService.getSuborderById(TWIN)).thenReturn(twin);
    when(suborderService.getSubordersByCustomerorderId(ORDER_ID)).thenReturn(List.of(twin));
    when(suborderService.getSelectableSubordersByCustomerorderId(anyLong(), (Long) any())).thenReturn(List.of(twin));
    when(suborderService.getSuborderChildren(TWIN)).thenReturn(List.of());
  }

  @Test
  void the_sign_proposed_for_the_top_level_follows_the_top_level_suborders() throws Exception {
    mockMvc.perform(get("/orders/suborders/sign").param("customerorderId", "7").param("parentId", ""))
        .andExpect(content().string("02"));
  }

  @Test
  void the_sign_proposed_below_the_suborder_with_the_order_id_follows_its_children() throws Exception {
    mockMvc.perform(get("/orders/suborders/sign").param("customerorderId", "7").param("parentId", "7"))
        .andExpect(content().string("01"));
  }

  @Test
  void a_new_form_starts_on_the_top_level_with_the_validity_of_the_order() throws Exception {
    var form = form(mockMvc.perform(get("/orders/suborders/create").param("customerorderId", "7")).andReturn());

    assertThat(form.getParentId()).isNull();
    assertThat(form.getValidFrom()).isEqualTo("2026-01-01");
  }

  @Test
  void changing_the_customer_order_goes_back_to_the_top_level() throws Exception {
    var form = form(mockMvc.perform(post("/orders/suborders/change-customerorder")
        .param("customerId", "3").param("customerorderId", "7").param("parentId", "7")).andReturn());

    assertThat(form.getParentId()).isNull();
    assertThat(form.getValidFrom()).isEqualTo("2026-01-01");
  }

  @Test
  void choosing_the_suborder_with_the_order_id_as_parent_takes_its_validity() throws Exception {
    var form = form(mockMvc.perform(post("/orders/suborders/change-parent-order")
        .param("customerId", "3").param("customerorderId", "7").param("parentId", "7")).andReturn());

    assertThat(form.getParentId()).isEqualTo(TWIN);
    assertThat(form.getValidFrom()).isEqualTo("2026-03-01");
  }

  @Test
  void choosing_the_top_level_again_takes_the_validity_of_the_order() throws Exception {
    var form = form(mockMvc.perform(post("/orders/suborders/change-parent-order")
        .param("customerId", "3").param("customerorderId", "7").param("parentId", "")).andReturn());

    assertThat(form.getParentId()).isNull();
    assertThat(form.getValidFrom()).isEqualTo("2026-01-01");
  }

  @Test
  void a_top_level_suborder_opens_for_editing_on_the_top_level() throws Exception {
    var form = form(mockMvc.perform(get("/orders/suborders/7/edit")).andReturn());

    assertThat(form.getParentId()).isNull();
  }

  @Test
  void below_the_suborder_with_the_order_id_its_validity_bounds_the_dates() throws Exception {
    mockMvc.perform(store("7", "2026-02-01"))
        .andExpect(model().attributeHasFieldErrors("suborderForm", "validFrom"));
  }

  @Test
  void on_the_top_level_the_order_bounds_the_dates_and_no_parent_is_sent() throws Exception {
    mockMvc.perform(store("", "2026-02-01"));

    var data = ArgumentCaptor.forClass(SuborderDTO.class);
    verify(suborderService).create(data.capture(), eq(ORDER_ID));
    assertThat(data.getValue().parentId()).isNull();
  }

  /**
   * The training switch is a checkbox: unticked, the browser sends nothing at all. That arrives as
   * {@code false}, not as {@code null} — the column is {@code NOT NULL} since #1246.
   */
  @Test
  void a_suborder_stored_without_the_training_switch_is_no_training() throws Exception {
    mockMvc.perform(store("", "2026-02-01"));

    var data = ArgumentCaptor.forClass(SuborderDTO.class);
    verify(suborderService).create(data.capture(), eq(ORDER_ID));
    assertThat(data.getValue().trainingFlag()).isFalse();
  }

  @Test
  void a_ticked_training_switch_is_stored() throws Exception {
    mockMvc.perform(store("", "2026-02-01").param("trainingFlag", "true"));

    var data = ArgumentCaptor.forClass(SuborderDTO.class);
    verify(suborderService).create(data.capture(), eq(ORDER_ID));
    assertThat(data.getValue().trainingFlag()).isTrue();
  }

  /**
   * The sign is unique among the siblings, whatever their validity and whether hidden (#1333) — the
   * twin "01" ends long before the new suborder starts and is still in the way.
   */
  @Test
  void a_sign_a_sibling_carries_is_refused_whatever_its_validity() throws Exception {
    twin.setUntilDate(ORDER_FROM.plusDays(1));
    twin.setHide(true);
    when(suborderService.getSubordersByCustomerorderIds(List.of(ORDER_ID))).thenReturn(List.of(twin));

    mockMvc.perform(store("", "2026-06-01", "01"))
        .andExpect(model().attributeHasFieldErrors("suborderForm", "sign"));

    verify(suborderService, never()).create(any(), any());
  }

  @Test
  void the_same_sign_below_another_parent_is_no_sibling() throws Exception {
    when(suborderService.getSubordersByCustomerorderIds(List.of(ORDER_ID))).thenReturn(List.of(twin));

    mockMvc.perform(store(String.valueOf(TWIN), "2026-06-01", "01"));

    verify(suborderService).create(any(), eq(ORDER_ID));
  }

  private static MockHttpServletRequestBuilder store(String parentId, String validFrom) {
    return store(parentId, validFrom, "02");
  }

  private static MockHttpServletRequestBuilder store(String parentId, String validFrom, String sign) {
    return post("/orders/suborders/store")
        .param("customerId", "3")
        .param("customerorderId", "7")
        .param("parentId", parentId)
        .param("sign", sign)
        .param("description", "Leistung")
        .param("validFrom", validFrom)
        .param("validUntil", "")
        .param("debithours", "");
  }

  private static SuborderForm form(MvcResult result) {
    return (SuborderForm) result.getModelAndView().getModel().get("suborderForm");
  }

}
