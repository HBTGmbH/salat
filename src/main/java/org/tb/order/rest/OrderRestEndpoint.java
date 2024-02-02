package org.tb.order.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder.VisitorDirection;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@SecurityScheme(name = "apikey", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "x-api-key", description = "tokenId:secret")
@RequestMapping(path = "/rest/orders")
public class OrderRestEndpoint {

  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final SuborderDAO suborderDAO;
  private final AuthorizedUser authorizedUser;

  @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation(security = {@SecurityRequirement(name = "bearerAuth")})
  public List<OrderData> getValidEmployeeOrders(
      @RequestParam("refDate") @DateTimeFormat(iso = ISO.DATE) LocalDate refDate) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }

    if (refDate == null) {
      refDate = DateUtils.today();
    }
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
        authorizedUser.getEmployeeId(), refDate);
    if (employeecontract == null || employeecontract.getId() == null) {
      throw new ResponseStatusException(NOT_FOUND);
    }

    // The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
    // was added to the SuborderDao class!!!
    List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(
        employeecontract.getId(), refDate);

    final LocalDate requestedRefDate = refDate; // make final for stream processing
    return createSuborderDataHierarchical(suborders, employeecontract, requestedRefDate);
  }


  List<OrderData> createSuborderDataHierarchical(List<Suborder> suborders,
      Employeecontract employeecontract, LocalDate requestedRefDate) {
    return mergeOrders(suborders.stream().map(
            suborder -> createSuborderDataHierarchical(suborder, employeecontract, requestedRefDate))
        .toList());

  }

  private List<OrderData> mergeOrders(List<OrderData> orders) {

    boolean hasSuborders = orders.stream().map(OrderData::getSuborder).anyMatch(Objects::nonNull);
    if (hasSuborders) {
      return orders.stream().collect(Collectors.groupingBy(OrderData::getId)).values().stream().map(
          orderList -> orderList.get(0).toBuilder().suborder(mergeOrders(
                  orderList.stream()
                      .map(OrderData::getSuborder)
                      .filter(Objects::nonNull)
                      .flatMap(Collection::stream)
                      .toList()))
              .build()).toList();
    } else {
      return orders;
    }
  }

  private OrderData createSuborderDataHierarchical(Suborder s, Employeecontract employeecontract,
      LocalDate requestedRefDate) {
    AtomicReference<OrderData> res = new AtomicReference<>();
    AtomicReference<OrderData> last = new AtomicReference<>();
    s.acceptVisitor(suborder -> {
      if (res.get() == null) {
        Customerorder customerorder = suborder.getCustomerorder();

        OrderData root = OrderData.builder() // here is the customer-order additional added
            .label(customerorder.getShortdescription()).id(customerorder.getId())
            .commentRequired(true).build();
        res.set(root);
        last.set(root);
      }
      long employeeorderId = getEmployeeorderId(s, employeecontract, requestedRefDate);
      OrderData current = OrderData.builder().id(suborder.getId())
          .label(suborder.getShortdescription())
          .employeeorderId(employeeorderId)
          .commentRequired(suborder.getCommentnecessary()).build();

      last.get().setSuborder(Collections.singleton(current));

      last.set(current);
    }, VisitorDirection.PARENT);
    return res.get();
  }

  private Long getEmployeeorderId(Suborder order, Employeecontract employeecontract,
      LocalDate refDate) {
    if (employeecontract.getId() == null || order.getId() == null) {
      throw new ResponseStatusException(NOT_FOUND);
    }

    Employeeorder eo = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
        employeecontract.getId(),
        order.getId(),
        refDate
    );
    return eo.getId();
  }
}
