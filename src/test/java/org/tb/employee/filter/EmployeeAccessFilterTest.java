package org.tb.employee.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.tb.common.exception.ErrorCode.EC_NO_CURRENT_CONTRACT;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.WebUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.domain.EmployeeAccessDenial;

/**
 * #1054: Wer keinen heute gültigen Vertrag hat, bekommt eine Antwort, die den Grund nennt - und
 * zwar aus der Filterkette heraus, weil die Bedingung vor jedem Controller feststeht.
 */
class EmployeeAccessFilterTest {

  private final EmployeeAccessDenial employeeAccessDenial = new EmployeeAccessDenial();

  private EmployeeAccessFilter classUnderTest;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("org/tb/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    classUnderTest = new EmployeeAccessFilter(
        employeeAccessDenial,
        new ErrorCodeViewHelper(new MessageSourceAccessor(messageSource, Locale.GERMAN))
    );
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
  }

  @Test
  void passes_the_request_on_when_nothing_is_denied() throws Exception {
    classUnderTest.doFilter(request("/dailyreport/dashboard"), response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void answers_a_page_request_with_403_and_the_reason() throws Exception {
    deny();

    classUnderTest.doFilter(request("/dailyreport/dashboard"), response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(FORBIDDEN.value());
    // Die Fehlerseite zeigt diese Meldung; der Text kommt aus dem Nachrichtenbuendel, nicht aus
    // dem Code.
    assertThat(response.getErrorMessage())
        .isEqualTo("Für testy ist heute kein Mitarbeitervertrag gültig. Bitte an das Backoffice wenden.");
  }

  @Test
  void answers_an_api_request_with_a_problem_document() throws Exception {
    deny();

    classUnderTest.doFilter(request("/api/reports/execute"), response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(FORBIDDEN.value());
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("\"status\":403")
        .contains(EC_NO_CURRENT_CONTRACT.getCode())
        .contains(EC_NO_CURRENT_CONTRACT.getMessage());
  }

  /**
   * Die Weiterleitung auf {@code /error} rendert die Fehlerseite. Griffe der Filter dort erneut,
   * beantwortete er seine eigene Antwort - und Tomcat schickte am Ende seine 500-Seite.
   */
  @Test
  void keeps_out_of_the_error_dispatch() throws Exception {
    deny();
    var request = request("/error");
    request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, "/dailyreport/dashboard");

    classUnderTest.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void keeps_delivering_static_files_so_the_error_page_stays_readable() throws Exception {
    deny();

    classUnderTest.doFilter(request("/css/salat.css"), response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void leaves_the_way_out_of_an_impersonation_open() throws Exception {
    deny();

    classUnderTest.doFilter(request("/auth/exit-impersonation"), response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private void deny() {
    employeeAccessDenial.deny(error(EC_NO_CURRENT_CONTRACT, "testy"));
  }

  private static MockHttpServletRequest request(String path) {
    var request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }

}
