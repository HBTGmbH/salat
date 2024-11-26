package org.tb.customer.persistence;

import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Component;
import org.tb.customer.Customer_;
import org.tb.customer.domain.Customer;
import org.tb.employee.domain.Employee;

@Component
@RequiredArgsConstructor
public class CustomerDAO {

    private final CustomerRepository customerRepository;

    /**
     * Get a list of all Customers ordered by name.
     */
    public List<Customer> getCustomers() {
        return Lists.newArrayList(customerRepository.findAll(Sort.by(Customer_.NAME)));
    }

    /**
     * Get a list of all Customers ordered by name.
     */
    public List<Customer> getCustomersByFilter(String filter) {
        if (filter == null || filter.trim().equals("")) {
            return getCustomers();
        } else {
            filter = "%" + filter + "%";
            return customerRepository.findAllByFilterIgnoringCase(filter);
        }
    }

    /**
     * Get a list of all Customers ordered by short name.
     */
    public List<Customer> getCustomersOrderedByShortName() {
        var order = new Order(ASC, Customer_.SHORTNAME).ignoreCase();
        return Lists.newArrayList(customerRepository.findAll(Sort.by(order)));
    }

    /**
     * Gets the customer for the given id.
     */
    public Customer getCustomerById(long id) {
        return customerRepository.findById(id).orElse(null);
    }

    /**
     * Calls {@link CustomerDAO#save(Customer, Employee)} with {@link Employee} = null.
     */
    public void save(Customer cu) {
        customerRepository.save(cu);
    }

    /**
     * Deletes the given customer .
     */
    public boolean deleteCustomerById(long cuId) {
        Customer cu = getCustomerById(cuId);

        if (cu != null) {
            // check if related customerorders exist - if so, no deletion possible
            if (cu.getCustomerorders() != null && !cu.getCustomerorders().isEmpty()) return false;

            customerRepository.delete(cu);
            return true;
        }

        return false;
    }
}
