package org.tb.customer;

import static org.tb.common.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.ErrorCode.CU_CUSTOMER_ORDERS_EXIST;
import static org.tb.common.ErrorCode.CU_NOT_FOUND;

import java.util.List;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;

@Service
@Data
@RequiredArgsConstructor
@Transactional
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final AuthorizedUser authorizedUser;

  @Transactional(readOnly = true)
  public List<CustomerDTO> list() {
    return StreamSupport
        .stream(customerRepository.findAll(Sort.by(Customer_.NAME)).spliterator(), false)
        .map(CustomerDTO::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CustomerDTO> list(String filter) {
    if (filter == null || filter.isBlank()) {
      return list();
    } else {
      return customerRepository
          .findAllByFilterIgnoringCase("%" + filter + "%").stream()
          .map(CustomerDTO::from)
          .toList();
    }
  }

  public CustomerDTO save(CustomerDTO customerDTO) {
    if(!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
    if(customerDTO.isNew()) {
      Customer customer = new Customer();
      customerDTO.copyTo(customer);
      return CustomerDTO.from(customerRepository.save(customer));
    }
    Customer customer = customerRepository
        .findById(customerDTO.getId())
        .orElseThrow(() -> new InvalidDataException(CU_NOT_FOUND));
    customerDTO.copyTo(customer);
    return CustomerDTO.from(customerRepository.save(customer));
  }

  public void delete(long id) {
    if(!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
    Customer customer = customerRepository
        .findById(id)
        .orElseThrow(() -> new InvalidDataException(CU_NOT_FOUND));
    // check if related customerorders exist - if so, no deletion possible
    if (customer.getCustomerorders() != null && !customer.getCustomerorders().isEmpty()) {
      throw new BusinessRuleException(CU_CUSTOMER_ORDERS_EXIST);
    }
    customerRepository.delete(customer);
  }

  @Transactional(readOnly = true)
  public CustomerDTO get(Long id) {
    return customerRepository
        .findById(id)
        .map(CustomerDTO::from)
        .orElseThrow(() -> new InvalidDataException(CU_NOT_FOUND));
  }

}
