package de.hbt.salat.jira;

import static org.springframework.test.util.ReflectionTestUtils.setField;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;

/**
 * Orders and suborders for the tests of the JIRA module (#1368). Replications, tickets and worklogs
 * refer to them as references with a foreign key, so a test that stores them needs the order tree
 * in the database; a test with mocked repositories gets by with an entity that only carries its id.
 */
public final class OrderTree {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  private final EntityManager entityManager;
  private final String customerShortname;
  private Customer customer;

  /**
   * @param entityManager persists inside the caller's transaction
   * @param customerShortname unique across the test database — a {@code @SpringBootTest} shares it
   *     with every other test of the same context
   */
  public OrderTree(EntityManager entityManager, String customerShortname) {
    this.entityManager = entityManager;
    this.customerShortname = customerShortname;
  }

  public OrderTree(EntityManager entityManager) {
    this(entityManager, "JIRA-TEST");
  }

  public Customerorder customerorder(String sign) {
    var order = new Customerorder();
    order.setCustomer(customer());
    order.setSign(sign);
    order.setShortdescription("Kurz");
    order.setDescription("Auftrag");
    order.setFromDate(FROM);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(false);
    entityManager.persist(order);
    return order;
  }

  public Suborder suborder(Customerorder customerorder, Suborder parent, String sign) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setParentorder(parent);
    suborder.setSign(sign);
    suborder.setShortdescription("Kurz");
    suborder.setDescription("Leistung");
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    if (parent != null) {
      parent.addSuborder(suborder);
    }
    entityManager.persist(suborder);
    return suborder;
  }

  /** Removes what {@link #customerorder} created, suborders first; for tests without rollback. */
  public void remove(Customerorder customerorder) {
    entityManager.createQuery("delete from Suborder s where s.customerorder.id = :id and s.parentorder is not null")
        .setParameter("id", customerorder.getId()).executeUpdate();
    entityManager.createQuery("delete from Suborder s where s.customerorder.id = :id")
        .setParameter("id", customerorder.getId()).executeUpdate();
    entityManager.createQuery("delete from Customerorder c where c.id = :id")
        .setParameter("id", customerorder.getId()).executeUpdate();
    entityManager.createQuery("delete from Customer c where c.shortname = :shortname")
        .setParameter("shortname", customerShortname).executeUpdate();
  }

  /** An order that exists only as its id, for tests whose repositories are mocks. */
  public static Customerorder customerorderWithId(long id) {
    var order = new Customerorder();
    setField(order, "id", id);
    return order;
  }

  /** A suborder that exists only as its id and its order, likewise. */
  public static Suborder suborderWithId(long id, Customerorder customerorder) {
    var suborder = new Suborder();
    setField(suborder, "id", id);
    suborder.setCustomerorder(customerorder);
    return suborder;
  }

  private Customer customer() {
    if (customer == null) {
      customer = new Customer();
      customer.setShortname(customerShortname);
      customer.setName("Testkunde");
      customer.setAddress("Teststraße 1");
      entityManager.persist(customer);
    }
    return customer;
  }
}
