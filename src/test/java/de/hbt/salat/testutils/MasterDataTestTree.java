package de.hbt.salat.testutils;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;

/**
 * Orders, suborders and people stored for a repository test, named by sign (#1367). A module that
 * refers to them as references (ADR-0036) has a foreign key on them, so a test that stores such a
 * record needs them in the database; asking twice for the same sign gives the same record.
 */
public final class MasterDataTestTree {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  private final EntityManager entityManager;
  private final Map<String, Customerorder> customerorders = new HashMap<>();
  private final Map<String, Suborder> suborders = new HashMap<>();
  private final Map<String, Employee> employees = new HashMap<>();
  private Customer customer;

  /** @param entityManager persists inside the caller's transaction */
  public MasterDataTestTree(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public Customerorder customerorder(String sign) {
    return customerorders.computeIfAbsent(sign, key -> {
      var order = new Customerorder();
      order.setCustomer(customer());
      order.setSign(key);
      order.setShortdescription(key);
      order.setDescription(key);
      order.setFromDate(FROM);
      order.setOrderType(OrderType.STANDARD);
      order.setDebithours(Duration.ZERO);
      order.setHide(false);
      entityManager.persist(order);
      return order;
    });
  }

  /** A suborder directly below its order. */
  public Suborder suborder(String customerorderSign, String sign) {
    return suborders.computeIfAbsent(customerorderSign + "/" + sign, key -> {
      var suborder = new Suborder();
      suborder.setCustomerorder(customerorder(customerorderSign));
      suborder.setSign(sign);
      suborder.setShortdescription(sign);
      suborder.setDescription(sign);
      suborder.setFromDate(FROM);
      suborder.setDebithours(Duration.ZERO);
      suborder.setHide(false);
      entityManager.persist(suborder);
      return suborder;
    });
  }

  public Employee employee(String sign) {
    return employees.computeIfAbsent(sign, key -> {
      var employee = new Employee();
      employee.setSign(key);
      employee.setFirstname(key);
      employee.setLastname(key);
      employee.setGender(GlobalConstants.GENDER_FEMALE);
      employee.setHide(false);
      entityManager.persist(employee);
      return employee;
    });
  }

  private Customer customer() {
    if (customer == null) {
      customer = new Customer();
      customer.setShortname(uniqueShortname("MD"));
      customer.setName("Testkunde");
      customer.setAddress("Teststraße 1");
      entityManager.persist(customer);
    }
    return customer;
  }
}
