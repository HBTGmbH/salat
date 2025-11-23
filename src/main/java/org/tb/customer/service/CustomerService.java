package org.tb.customer.service;

import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.exception.ErrorCode.CU_NOT_FOUND;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.customer.domain.Customer;
import org.tb.customer.domain.CustomerDTO;
import org.tb.customer.domain.Customer_;
import org.tb.customer.event.CustomerDeleteEvent;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.customer.persistence.CustomerRepository;

@Service
@Data
@RequiredArgsConstructor
@Transactional
@Authorized
public class CustomerService {

  private final ApplicationEventPublisher eventPublisher;
  private final CustomerRepository customerRepository;
  private final AuthorizedUser authorizedUser;
  private final CustomerDAO customerDAO;

  @Transactional(readOnly = true)
  public List<CustomerDTO> getAllCustomerDTOs() {
    return StreamSupport
        .stream(customerRepository.findAll(Sort.by(Customer_.NAME)).spliterator(), false)
        .map(CustomerDTO::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CustomerDTO> getAllCustomerDTOsByFilter(String filter) {
    if (filter == null || filter.isBlank()) {
      return getAllCustomerDTOs();
    } else {
      return customerRepository
          .findAllByFilterIgnoringCase("%" + filter + "%").stream()
          .map(CustomerDTO::from)
          .toList();
    }
  }

  @Authorized(requiresManager = true)
  public void createOrUpdate(CustomerDTO customerDTO) {
    if(!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }

    Customer customer = new Customer();
    if(!customerDTO.isNew()) {
      customer = customerRepository
          .findById(customerDTO.getId())
          .orElseThrow(() -> new InvalidDataException(CU_NOT_FOUND));
    }
    customerDTO.copyTo(customer);
    customerRepository.save(customer);
    customerDTO.setId(customer.getId());
  }

  @Authorized(requiresManager = true)
  public void deleteCustomerById(long id) {
    if(!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
    Customer customer = customerRepository
        .findById(id)
        .orElseThrow(() -> new InvalidDataException(CU_NOT_FOUND));

    var event = new CustomerDeleteEvent(id);
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(
          ErrorCode.CU_DELETE_GOT_VETO,
          customer.getShortname()
      ));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }

    customerRepository.delete(customer);
  }

  @Transactional(readOnly = true)
  public CustomerDTO getCustomerById(Long id) {
    return customerRepository
        .findById(id)
        .map(CustomerDTO::from)
        .orElseThrow(() -> new InvalidDataException(CU_NOT_FOUND));
  }

  public List<Customer> getAllCustomers() {
    return customerDAO.getCustomers();
  }

  public List<Customer> getCustomersOrderedByShortName() {
    return customerDAO.getCustomersOrderedByShortName();
  }
}
