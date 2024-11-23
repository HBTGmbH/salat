package org.tb.chicoree;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.tb.auth.service.AuthService;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Component
@RequiredArgsConstructor
public class ChicoreeAutoLoginHandler implements ApplicationListener<AuthenticationSuccessEvent> {

  public static final Random RANDOM = new Random();
  private final AuthorizedUser authorizedUser;
  private final ChicoreeSessionStore chicoreeSessionStore;
  private final EmployeecontractService employeecontractService;
  private final EmployeeService employeeService;
  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final AuthService authService;

  @SneakyThrows
  @Override
  public void onApplicationEvent(AuthenticationSuccessEvent event) {

    // no further stuff for REST API calls - all is just for struts and old web UI
    if(request.getRequestURL().toString().contains("/api/") || request.getRequestURL().toString().contains("/rest/")) return;

    if(chicoreeSessionStore.getLoginEmployeecontractId().isEmpty()) {
      Authentication authentication = event.getAuthentication();
      authorizedUser.login(authentication.getName());
      authService.initAuthorizedUser(authentication, authorizedUser);
      Employee employee = employeeService.getLoginEmployee();
      Optional<Employeecontract> employeecontract = employeecontractService.getCurrentContract(employee.getId());
      if(employeecontract.isPresent()) {
        chicoreeSessionStore.setGreeting(getRandomGreeting());
        chicoreeSessionStore.setLoginEmployee(employee);
        chicoreeSessionStore.setLoginEmployeecontractId(employeecontract.get().getId());
        chicoreeSessionStore.setDashboardDate(today());
      } else {
        response.sendError(HttpStatus.FORBIDDEN.value(), "No valid contract found for " + employee.getSign());
      }
    } else {
      // already logged in
    }
  }

  public static String getRandomGreeting() {
    int rnd = RANDOM.nextInt(greetings.length);
    return greetings[rnd];
  }

  private static final String[] greetings = new String[] {
      "Bonjour ", "Salut ",
      "Hola ", "¿Qué tal? ",
      "Salve ", "Ciao ",
      "Guten Tag, ", "Hallo ",
      "Olá ", "Oi ",
      "Konnichiwa ", "Yā, Yō ",
      "Asalaam alaikum ", "Ahlan ",
      "Goddag ", "Hej ", "Halløj ",
      "Goedendag ", "Hoi ",
      "Yassas ",
      "Dzień dobry ", "Cześć ",
      "Namaste ",
      "Merhaba ", "Selam ",
      "Shalom ",
      "God dag ",
      "Dobriy den ",
      "Grüezi ",
      "Moin ",
      "Grüß Gott, ", "Servus "
  };

}
