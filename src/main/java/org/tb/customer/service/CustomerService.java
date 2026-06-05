package org.tb.customer.service;

import static java.util.stream.StreamSupport.stream;
import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.exception.ErrorCode.CU_DUPLICATE_SHORT_NAME;
import static org.tb.common.exception.ErrorCode.CU_NOT_FOUND;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
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
  public List<CustomerDTO> getAllCustomerDTOsByFilter(String filter, boolean showHidden) {
    boolean hasFilter = filter != null && !filter.isBlank();
    var repo = showHidden
        ? stream(customerRepository.findAll(Sort.by(Customer_.NAME)).spliterator(), false)
        : customerRepository.findAllVisible().stream();
    return repo
        .filter(c -> !hasFilter || filterMatchesInMemory(c, filter))
        .map(CustomerDTO::from)
        .toList();
  }

  private boolean filterMatchesInMemory(Customer c, String filter) {
    var upper = filter.toUpperCase();
    return containsIgnoreCase(c.getShortname(), upper)
        || containsIgnoreCase(c.getName(), upper)
        || containsIgnoreCase(c.getAddress(), upper);
  }

  private static boolean containsIgnoreCase(String value, String upper) {
    return value != null && value.toUpperCase().contains(upper);
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

    if(!Objects.equals(customer.getShortname(), customerDTO.getShortName())) {
      // ensure name uniqueness
      Predicate<Customer> notSameId = (Customer c) -> !Objects.equals(c.getId(), customerDTO.getId());
      Predicate<Customer> sameShortName = (Customer c) -> Objects.equals(c.getShortname(), customerDTO.getShortName());
      var duplicateFound = stream(customerRepository.findAll().spliterator(), false)
          .filter(notSameId)
          .anyMatch(sameShortName);
      if(duplicateFound) {
        throw new BusinessRuleException(CU_DUPLICATE_SHORT_NAME);
      }
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

  public Customer getCustomerEntityById(Long id) {
    return customerDAO.getCustomerById(id);
  }

  public List<Customer> getAllCustomers() {
    return customerDAO.getCustomers();
  }

  public List<Customer> getCustomersOrderedByShortName() {
    return customerDAO.getCustomersOrderedByShortName();
  }
}
