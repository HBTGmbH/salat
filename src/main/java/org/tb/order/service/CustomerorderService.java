package org.tb.order.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.util.DurationUtils;
import org.tb.customer.CustomerDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.event.CustomerorderDeleteEvent;
import org.tb.order.event.CustomerorderUpdateEvent;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.CustomerorderRepository;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomerorderService {

  private final ApplicationEventPublisher eventPublisher;
  private final CustomerorderDAO customerorderDAO;
  private final CustomerDAO customerDAO;
  private final EmployeeDAO employeeDAO;
  private final CustomerorderRepository customerorderRepository;

  public List<Customerorder> getCustomerordersWithValidEmployeeOrders(long employeeContractId, final LocalDate date) {
    return customerorderDAO.getCustomerordersWithValidEmployeeOrders(employeeContractId, date);
  }

  public List<ServiceFeedbackMessage> create(long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
      String description, String shortdescription, String orderCustomer, String responsibleCustomerContractually,
      String responsibleCustomerTechnical, long responsibleHbtId, long respEmpHbtContractId, String debithours,
      Byte debithoursunit, int statusreport, Boolean hide, OrderType orderType) {
    return createOrUpdate(
        null,
        customerId,
        fromDate,
        untilDate,
        sign,
        description,
        shortdescription,
        orderCustomer,
        responsibleCustomerContractually,
        responsibleCustomerTechnical,
        responsibleHbtId,
        respEmpHbtContractId,
        debithours,
        debithoursunit,
        statusreport,
        hide,
        orderType
    );
  }

  public List<ServiceFeedbackMessage> update(long customerorderId, long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
      String description, String shortdescription, String orderCustomer, String responsibleCustomerContractually,
      String responsibleCustomerTechnical, long responsibleHbtId, long respEmpHbtContractId, String debithours,
      Byte debithoursunit, int statusreport, Boolean hide, OrderType orderType) {
    return createOrUpdate(
        customerorderId,
        customerId,
        fromDate,
        untilDate,
        sign,
        description,
        shortdescription,
        orderCustomer,
        responsibleCustomerContractually,
        responsibleCustomerTechnical,
        responsibleHbtId,
        respEmpHbtContractId,
        debithours,
        debithoursunit,
        statusreport,
        hide,
        orderType
    );
  }

  private List<ServiceFeedbackMessage> createOrUpdate(Long coId, long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
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

    if(!co.isNew()) {
      var event = new CustomerorderUpdateEvent(co);
      eventPublisher.publishEvent(event);
      if(event.isVetoed()) {
        return event.getMessages();
      }
    }

    customerorderRepository.save(co);

    return List.of();
  }

  public Customerorder getCustomerorderBySign(String selectedOrder) {
    return customerorderDAO.getCustomerorderBySign(selectedOrder);
  }

  public List<Customerorder> getCustomerordersByEmployeeContractId(long employeeContractId) {
    return customerorderDAO.getCustomerordersByEmployeeContractId(employeeContractId);
  }

  public List<Customerorder> getAllCustomerorders() {
    return customerorderDAO.getCustomerorders();
  }

  public List<Customerorder> getInvoiceableCustomerorders() {
    return customerorderDAO.getInvoiceableCustomerorders();
  }

  public Customerorder getCustomerorderById(long customerorderId) {
    return customerorderDAO.getCustomerorderById(customerorderId);
  }

  public List<Customerorder> getCustomerOrdersByResponsibleEmployeeId(Long responsibleEmployeeId) {
    return customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(responsibleEmployeeId);
  }

  public List<Customerorder> getVisibleCustomerorders() {
    return customerorderDAO.getVisibleCustomerorders();
  }

  public List<Customerorder> getVisibleCustomerOrdersByResponsibleEmployeeId(Long responsibleEmployeeId) {
    return customerorderDAO.getVisibleCustomerOrdersByResponsibleEmployeeId(responsibleEmployeeId);
  }

  public List<ServiceFeedbackMessage> deleteCustomerorderById(long customerOrderId) {
    var event = new CustomerorderDeleteEvent(customerOrderId);
    eventPublisher.publishEvent(event);
    if(event.isVetoed()) {
      return event.getMessages();
    }
    customerorderRepository.deleteById(customerOrderId);
    return List.of();
  }

  public List<Customerorder> getCustomerordersByFilters(Boolean showInvalid, String filter, Long customerId) {
    return customerorderDAO.getCustomerordersByFilters(showInvalid, filter, customerId);
  }

}
