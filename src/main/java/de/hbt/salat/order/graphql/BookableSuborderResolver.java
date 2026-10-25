package de.hbt.salat.order.graphql;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.employee.graphql.MeResolver.Me;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.BookableSuborder;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * What the person may book on (concept 4, {@code Me.bookableSuborders}): {@code order} extends the type of
 * {@code employee}, the direction {@code ArchitectureTest} allows (concept 3.7). The same condition as the booking
 * form, through {@link EmployeeorderService#getBookableSuborders}.
 */
@Controller
@Authorized
@RequiredArgsConstructor
class BookableSuborderResolver {

  private final EmployeecontractService employeecontractService;
  private final EmployeeorderService employeeorderService;

  @SchemaMapping(typeName = "Me")
  @Authorized
  List<BookableSuborder> bookableSuborders(Me me, @Argument LocalDate on) {
    return employeecontractService.getEmployeecontractPeriodsBetween(me.employeeId(), on, on).stream()
        .findFirst()
        .map(period -> employeeorderService.getBookableSuborders(period.employeecontractId(), on))
        .orElse(List.of());
  }
}
