package org.tb.order.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.util.DurationUtils;
import org.tb.customer.CustomerDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.SuborderDAO;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomerorderService {

  private final CustomerorderDAO customerorderDAO;
  private final CustomerDAO customerDAO;
  private final EmployeeDAO employeeDAO;
  private final SuborderDAO suborderDAO;
  private final SuborderService suborderService;

  public Customerorder createOrUpdateOrder(Long coId, long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
      String description, String shortdescription, String orderCustomer, String responsibleCustomerContractually,
      String responsibleCustomerTechnical, long responsibleHbtId, long respEmpHbtContractId, String debithours,
      Byte debithoursunit, int statusreport, Boolean hide, OrderType orderType) {

    Customerorder co;
    if (coId != null) {
      co = customerorderDAO.getCustomerorderById(coId);
    } else {
      // new customer order
      co = new Customerorder();
    }

    /* set attributes */
    co.setCustomer(customerDAO.getCustomerById(customerId));

    co.setUntilDate(untilDate);
    co.setFromDate(fromDate);

    co.setSign(sign);
    co.setDescription(description);
    co.setShortdescription(shortdescription);
    co.setOrder_customer(orderCustomer);

    co.setResponsible_customer_contractually(responsibleCustomerContractually);
    co.setResponsible_customer_technical(responsibleCustomerTechnical);

    Employee responsibleHbt = employeeDAO.getEmployeeById(responsibleHbtId);
    Employee respEmpHbtContract = employeeDAO.getEmployeeById(respEmpHbtContractId);
    co.setResponsible_hbt(responsibleHbt);
    co.setRespEmpHbtContract(respEmpHbtContract);

    if (debithours == null
        || debithours.isEmpty()
        || DurationUtils.parseDuration(debithours).isZero()) {
      co.setDebithours(Duration.ZERO);
      co.setDebithoursunit(null);
    } else {
      co.setDebithours(DurationUtils.parseDuration(debithours));
      co.setDebithoursunit(debithoursunit);
    }

    co.setStatusreport(statusreport);
    co.setHide(hide);

    co.setOrderType(orderType);

    customerorderDAO.save(co);
    coId = co.getId();

    /* adjust suborders */
    List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(coId, false);
    if (suborders != null && !suborders.isEmpty()) {
      for (Suborder so : suborders) {
        suborderService.adjustValidity(so, co.getFromDate(), co.getUntilDate());
      }
    }

    return co;
  }

}
