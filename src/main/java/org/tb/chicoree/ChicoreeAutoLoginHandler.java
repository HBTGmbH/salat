package org.tb.chicoree;

import static java.lang.Boolean.TRUE;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChicoreeAutoLoginHandler implements ApplicationListener<AuthorizedUserChangedEvent> {

  public static final Random RANDOM = new Random();
  private final AuthorizedUser authorizedUser;
  private final ChicoreeSessionStore chicoreeSessionStore;
  private final EmployeecontractService employeecontractService;
  private final EmployeeService employeeService;
  private final HttpServletRequest request;

  @Override
  public void onApplicationEvent(AuthorizedUserChangedEvent event) {

    // no further stuff for REST API calls - all is just for struts and old web UI
    if(request.getRequestURL().toString().contains("/api/") || request.getRequestURL().toString().contains("/rest/")) return;

    if(chicoreeSessionStore.getLoginEmployeecontractId().isEmpty()) {
      Employee loginEmployee = employeeService.getLoginEmployee();
      if(loginEmployee == null) {
        log.error("No matching employee found for {}.", authorizedUser.getSign());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No matching employee found for " + authorizedUser.getSign());
      }
      authorizedUser.init(loginEmployee.getId(), TRUE == loginEmployee.isRestricted(), loginEmployee.getStatus());
      Optional<Employeecontract> employeecontract = employeecontractService.getCurrentContract(loginEmployee.getId());
      if(employeecontract.isPresent()) {
        chicoreeSessionStore.setGreeting(getRandomGreeting());
        chicoreeSessionStore.setLoginEmployee(loginEmployee);
        chicoreeSessionStore.setLoginEmployeecontractId(employeecontract.get().getId());
        chicoreeSessionStore.setDashboardDate(today());
      } else {
        log.error("No valid contract found for {}.", loginEmployee.getSign());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No valid contract found for " + loginEmployee.getSign());
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
