package de.hbt.salat.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.common.viewhelper.NoticeViewHelper;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SpecialOrders;

/**
 * Das Formular lehnt eine Auftragsnummer ab, die ein anderer Auftrag trägt (#1208) — bis dahin nur beim Anlegen,
 * jetzt auch beim Bearbeiten, wo das Umbenennen auf eine vergebene Nummer erst am Unique-Schlüssel scheitern würde.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerorderControllerSignTest {

  private static final long HOLDER_ID = 7L;
  private static final long OTHER_ID = 8L;

  @Mock private CustomerorderService customerorderService;
  @Mock private CustomerService customerService;
  @Mock private EmployeeService employeeService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    var messages = new MessageSourceAccessor(messageSource, Locale.GERMANY);
    var controller = new CustomerorderController(customerorderService, customerService, employeeService, messages,
        new ErrorCodeViewHelper(messages), mock(AuthorizedEmployee.class), mock(FilterHintViewHelper.class),
        mock(NoticeViewHelper.class), mock(SpecialOrders.class));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    var holder = new Customerorder();
    setField(holder, "id", HOLDER_ID);
    holder.setSign("4711");
    when(customerorderService.getAllCustomerorders()).thenReturn(List.of(holder));
  }

  @Test
  void a_new_order_cannot_take_a_sign_that_is_held() throws Exception {
    assertThat(signErrors(null, "4711")).isEqualTo(1);
  }

  @Test
  void another_order_cannot_be_renamed_onto_it() throws Exception {
    assertThat(signErrors(OTHER_ID, "4711")).isEqualTo(1);
  }

  @Test
  void the_holder_keeps_its_own_sign() throws Exception {
    assertThat(signErrors(HOLDER_ID, "4711")).isZero();
  }

  /** Nur Nummer und Beginn sind ausgefüllt; die übrigen Felder scheitern und halten das Formular offen. */
  private int signErrors(Long id, String sign) throws Exception {
    var request = post("/orders/customerorders/store").param("sign", sign).param("validFrom", "2026-01-01");
    if (id != null) {
      request.param("id", id.toString());
    }
    var model = mockMvc.perform(request).andReturn().getModelAndView().getModel();
    return ((BindingResult) model.get(BindingResult.MODEL_KEY_PREFIX + "customerorderForm")).getFieldErrorCount("sign");
  }
}
