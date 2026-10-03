package de.hbt.salat.employee.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;

/**
 * Das Formular lehnt ein Kürzel ab, das ein anderer Mitarbeiter trägt (#1208), bevor der Unique-Schlüssel der
 * Datenbank es tut — beim Anlegen wie beim Bearbeiten, und gefragt ohne Leseregeln, damit auch ein versteckter
 * Träger zählt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeControllerSignTest {

  private static final long HOLDER_ID = 4L;
  private static final long OTHER_ID = 5L;

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

    var holder = new Employee();
    setField(holder, "id", HOLDER_ID);
    holder.setSign("abc");
    when(employeeService.getEmployeeBySign("abc")).thenReturn(holder);
  }

  @Test
  void a_new_employee_cannot_take_a_sign_that_is_held() throws Exception {
    assertThat(signErrors(null, "abc")).isEqualTo(1);
  }

  @Test
  void another_employee_cannot_be_renamed_onto_it() throws Exception {
    assertThat(signErrors(OTHER_ID, "abc")).isEqualTo(1);
  }

  @Test
  void the_holder_keeps_its_own_sign() throws Exception {
    assertThat(signErrors(HOLDER_ID, "abc")).isZero();
  }

  @Test
  void a_free_sign_passes() throws Exception {
    assertThat(signErrors(null, "xyz")).isZero();
  }

  /** Nur das Kürzel ist ausgefüllt; die übrigen Felder scheitern und halten das Formular offen. */
  private int signErrors(Long id, String sign) throws Exception {
    var request = post("/employees/store").param("sign", sign);
    if (id != null) {
      request.param("id", id.toString());
    }
    var model = mockMvc.perform(request).andReturn().getModelAndView().getModel();
    return ((BindingResult) model.get(BindingResult.MODEL_KEY_PREFIX + "employeeForm")).getFieldErrorCount("sign");
  }
}
