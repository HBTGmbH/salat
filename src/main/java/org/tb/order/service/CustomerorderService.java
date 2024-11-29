package org.tb.order.service;

import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.order.command.GetTimereportMinutesCommandEvent.OrderType.CUSTOMER;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.command.CommandPublisher;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.common.util.DurationUtils;
import org.tb.customer.event.CustomerDeleteEvent;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.order.command.GetTimereportMinutesCommandEvent;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.event.CustomerorderDeleteEvent;
import org.tb.order.event.CustomerorderUpdateEvent;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.CustomerorderRepository;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class CustomerorderService {

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final CustomerorderDAO customerorderDAO;
  private final CustomerDAO customerDAO;
  private final EmployeeDAO employeeDAO;
  private final CustomerorderRepository customerorderRepository;

  public List<Customerorder> getCustomerordersWithValidEmployeeOrders(long employeeContractId, final LocalDate date) {
    return customerorderDAO.getCustomerordersWithValidEmployeeOrders(employeeContractId, date);
  }

  @Authorized(requiresManager = true)
  public void create(long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
      String description, String shortdescription, String orderCustomer, String responsibleCustomerContractually,
      String responsibleCustomerTechnical, long responsibleHbtId, long respEmpHbtContractId, String debithours,
      Byte debithoursunit, Boolean hide, OrderType orderType) {
    createOrUpdate(
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
        hide,
        orderType
    );
  }

  @Authorized(requiresManager = true)
  public void update(long customerorderId, long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
      String description, String shortdescription, String orderCustomer, String responsibleCustomerContractually,
      String responsibleCustomerTechnical, long responsibleHbtId, long respEmpHbtContractId, String debithours,
      Byte debithoursunit, Boolean hide, OrderType orderType) {
    createOrUpdate(
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
        hide,
        orderType
    );
  }

  private void createOrUpdate(Long coId, long customerId, LocalDate fromDate, LocalDate untilDate, String sign,
      String description, String shortdescription, String orderCustomer, String responsibleCustomerContractually,
      String responsibleCustomerTechnical, long responsibleHbtId, long respEmpHbtContractId, String debithours,
      Byte debithoursunit, Boolean hide, OrderType orderType) {

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

    co.setHide(hide);

    co.setOrderType(orderType);

    if(!co.isNew()) {
      var event = new CustomerorderUpdateEvent(co);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            ErrorCode.CO_UPDATE_GOT_VETO,
            co.getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }
    customerorderRepository.save(co);
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

  @Authorized(requiresManager = true)
  public void deleteCustomerorderById(long customerOrderId) {
    var event = new CustomerorderDeleteEvent(customerOrderId);
    var customerorder = customerorderDAO.getCustomerorderById(customerOrderId);
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(
          ErrorCode.CO_DELETE_GOT_VETO,
          customerorder.getSign()
      ));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    customerorderRepository.deleteById(customerOrderId);
  }

  public List<Customerorder> getCustomerordersByFilters(Boolean showInvalid, String filter, Long customerId) {
    return customerorderDAO.getCustomerordersByFilters(showInvalid, filter, customerId);
  }

  @EventListener
  void onCustomerDelete(CustomerDeleteEvent event) {
    var customerorders = customerorderRepository.findAllByCustomerId(event.getId());
    for (Customerorder customerorder : customerorders) {
      deleteCustomerorderById(customerorder.getId());
    }
  }

  public Duration getTotalDuration(long customerorderId) {
    var command = GetTimereportMinutesCommandEvent.builder()
        .orderType(CUSTOMER)
        .orderIds(List.of(customerorderId))
        .build();
    commandPublisher.publish(command);
    return command.getResult().get(customerorderId);
  }

}
