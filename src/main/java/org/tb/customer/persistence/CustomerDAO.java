package org.tb.customer.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.customer.domain.Customer;

@Component
@RequiredArgsConstructor
public class CustomerDAO {

    private final CustomerRepository customerRepository;

    /**
     * Get a list of visible (non-hidden) Customers ordered by name.
     */
    public List<Customer> getCustomers() {
        return customerRepository.findAllVisible();
    }

    /**
     * Get a list of visible (non-hidden) Customers ordered by short name.
     */
    public List<Customer> getCustomersOrderedByShortName() {
        return customerRepository.findAllVisibleOrderByShortnameIgnoreCase();
    }

    /**
     * Gets the customer for the given id.
     */
    public Customer getCustomerById(long id) {
        return customerRepository.findById(id).orElse(null);
    }

}
