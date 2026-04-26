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
import org.tb.order.domain.CustomerorderDTO;
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
  public void create(CustomerorderDTO dto) {
    createOrUpdate(null, dto);
  }

  @Authorized(requiresManager = true)
  public void update(long customerorderId, CustomerorderDTO dto) {
    createOrUpdate(customerorderId, dto);
  }

  private void createOrUpdate(Long coId, CustomerorderDTO dto) {

    Customerorder co;
    if (coId != null) {
      co = customerorderDAO.getCustomerorderById(coId);
    } else {
      // new customer order
      co = new Customerorder();
    }

    /* set attributes */
    co.setCustomer(customerDAO.getCustomerById(dto.customerId()));

    co.setUntilDate(dto.untilDate());
    co.setFromDate(dto.fromDate());

    co.setSign(dto.sign());
    co.setDescription(dto.description());
    co.setShortdescription(dto.shortdescription());
    co.setOrder_customer(dto.orderCustomer());

    co.setResponsible_customer_contractually(dto.responsibleCustomerContractually());
    co.setResponsible_customer_technical(dto.responsibleCustomerTechnical());

    Employee responsibleHbt = employeeDAO.getEmployeeById(dto.responsibleHbtId());
    Employee respEmpHbtContract = employeeDAO.getEmployeeById(dto.respEmpHbtContractId());
    co.setResponsible_hbt(responsibleHbt);
    co.setRespEmpHbtContract(respEmpHbtContract);

    if (dto.debithours() == null
        || dto.debithours().isEmpty()
        || DurationUtils.parseDuration(dto.debithours()).isZero()) {
      co.setDebithours(Duration.ZERO);
      co.setDebithoursunit(null);
    } else {
      co.setDebithours(DurationUtils.parseDuration(dto.debithours()));
      co.setDebithoursunit(dto.debithoursunit());
    }

    co.setHide(dto.hide());

    co.setOrderType(dto.orderType());

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
