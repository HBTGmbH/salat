package de.hbt.salat.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.order.auth.EmployeeorderAuthorization;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SearchHits;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.TagUsage;
import de.hbt.salat.tag.domain.Tag;
import de.hbt.salat.tag.domain.TagName;

/**
 * Orders and suborders carry tags over a join table each (#847, ADR-0042): the lists filter by them, deleting an
 * order or suborder leaves no rows behind, and the forms count how often a tag is used.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({AuthorizedUserAuditorAware.class, CustomerorderDAO.class, SuborderDAO.class, EmployeeorderDAO.class,
    EmployeeorderAuthorization.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderTagsTest {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  @Autowired private CustomerorderDAO customerorderDAO;
  @Autowired private SuborderDAO suborderDAO;
  @Autowired private CustomerorderRepository customerorderRepository;
  @Autowired private SuborderRepository suborderRepository;
  @Autowired private TestEntityManager entityManager;

  @MockitoBean private AuthorizedUser authorizedUser;

  private Customer customer;
  private Tag fixedPrice;
  private Tag cloud;
  private Tag framework;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    when(authorizedUser.isManager()).thenReturn(true);
    customer = new Customer();
    customer.setShortname("ACME");
    customer.setName("Acme Werke");
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);
    fixedPrice = tag("Festpreis");
    cloud = tag("Cloud");
    framework = tag("Rahmenvertrag A");
  }

  // --- Filter -----------------------------------------------------------------------------------

  /** The tags of a filter are alternatives (#847): one of them is enough. */
  @Test
  void the_order_list_shows_the_orders_carrying_any_of_the_tags() {
    order("A-1", fixedPrice);
    order("B-2", cloud);
    order("C-3", framework);
    order("D-4");

    assertThat(signs(customerorderDAO.findCustomerorders(null, null, null, null,
        List.of(fixedPrice.getId(), cloud.getId()), 500))).containsExactly("A-1", "B-2");
  }

  @Test
  void an_order_with_two_of_the_tags_is_one_row_and_counted_once() {
    order("A-1", fixedPrice, cloud);

    var hits = customerorderDAO.findCustomerorders(null, null, null, null, List.of(fixedPrice.getId(), cloud.getId()),
        500);

    assertThat(signs(hits)).containsExactly("A-1");
    assertThat(hits.total()).isEqualTo(1);
  }

  @Test
  void without_tags_the_order_list_is_not_restricted() {
    order("A-1", fixedPrice);
    order("B-2");

    assertThat(signs(customerorderDAO.findCustomerorders(null, null, null, null, List.of(), 500)))
        .containsExactly("A-1", "B-2");
  }

  /** The suborder list shows what was tagged: the tags of the order above do not count (#847). */
  @Test
  void the_suborder_list_matches_only_the_tags_of_the_suborder_itself() {
    var order = order("ORD", fixedPrice);
    suborder(order, "01");
    suborder(order, "02", fixedPrice);

    assertThat(completeSigns(suborderDAO.findSuborders(null, null, null, null, null, List.of(fixedPrice.getId()), 500)))
        .containsExactly("ORD/02");
  }

  /** The list shows the tags of every row: one statement for all of them, not one per row (@BatchSize). */
  @Test
  void the_tags_of_a_list_are_read_in_one_statement_for_all_rows() {
    order("A-1", fixedPrice);
    order("B-2", cloud, framework);
    order("C-3", fixedPrice, cloud);
    entityManager.flush();
    entityManager.clear();
    var rows = customerorderDAO.findCustomerorders(null, null, null, null, List.of(), 500).rows();
    var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class)
        .getStatistics();
    statistics.clear();

    var tags = rows.stream().map(order -> order.getTags().size()).toList();

    assertThat(tags).containsExactly(1, 2, 2);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  // --- Löschen ----------------------------------------------------------------------------------

  @Test
  void deleting_an_order_leaves_no_rows_in_its_join_table_and_keeps_the_tags() {
    var order = order("A-1", fixedPrice, cloud);
    var other = order("B-2", fixedPrice);
    entityManager.flush();
    entityManager.clear();

    customerorderRepository.deleteById(order.getId());
    entityManager.flush();

    assertThat(rows("customerorder_tag")).isEqualTo(1);
    assertThat(entityManager.find(Tag.class, cloud.getId())).isNotNull();
    assertThat(customerorderRepository.findById(other.getId()).orElseThrow().getTags())
        .extracting(Tag::getName).containsExactly("Festpreis");
  }

  @Test
  void deleting_a_suborder_leaves_no_rows_in_its_join_table() {
    var order = order("ORD");
    var suborder = suborder(order, "01", fixedPrice, cloud);
    suborder(order, "02", cloud);
    entityManager.flush();
    entityManager.clear();

    suborderRepository.deleteById(suborder.getId());
    entityManager.flush();

    assertThat(rows("suborder_tag")).isEqualTo(1);
  }

  // --- Verwendung -------------------------------------------------------------------------------

  @Test
  void the_use_of_a_tag_is_counted_per_kind_of_object() {
    order("A-1", fixedPrice, cloud);
    var order = order("B-2", fixedPrice);
    suborder(order, "01", fixedPrice);
    entityManager.flush();

    assertThat(byTag(customerorderRepository.findTagUsages()))
        .containsExactlyInAnyOrderEntriesOf(Map.of(fixedPrice.getId(), 2L, cloud.getId(), 1L));
    assertThat(byTag(suborderRepository.findTagUsages())).containsExactlyInAnyOrderEntriesOf(Map.of(fixedPrice.getId(), 1L));
  }

  /** A list hands its tags out sorted by name, and as a copy: another module reaches the order (ADR-0036). */
  @Test
  void the_tags_of_an_order_are_handed_out_by_name() {
    var order = order("A-1", framework, cloud, fixedPrice);

    assertThat(order.getTags()).extracting(Tag::getName).containsExactly("Cloud", "Festpreis", "Rahmenvertrag A");
  }

  // --- Aufbau -----------------------------------------------------------------------------------

  private Tag tag(String name) {
    return entityManager.persist(new Tag(TagName.of(name)));
  }

  private Customerorder order(String sign, Tag... tags) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription("Beschreibung " + sign);
    order.setFromDate(FROM);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.replaceTags(List.of(tags));
    return entityManager.persist(order);
  }

  private Suborder suborder(Customerorder order, String sign, Tag... tags) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign(sign);
    suborder.setDescription("Unterauftrag");
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    suborder.replaceTags(List.of(tags));
    return entityManager.persist(suborder);
  }

  private long rows(String table) {
    return ((Number) entityManager.getEntityManager().createNativeQuery("select count(*) from " + table)
        .getSingleResult()).longValue();
  }

  private static Map<Long, Long> byTag(List<TagUsage> usages) {
    return usages.stream().collect(Collectors.toMap(TagUsage::tagId, TagUsage::count));
  }

  private static List<String> signs(SearchHits<Customerorder> hits) {
    return hits.rows().stream().map(Customerorder::getSign).toList();
  }

  private static List<String> completeSigns(SearchHits<Suborder> hits) {
    return hits.rows().stream().map(Suborder::getCompleteOrderSign).toList();
  }
}
