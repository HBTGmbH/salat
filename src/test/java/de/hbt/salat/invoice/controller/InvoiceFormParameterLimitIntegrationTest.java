package de.hbt.salat.invoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.budget.service.BudgetQueryService;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.invoice.domain.InvoiceGrouping;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * #1499: Druck, Export und Neugruppieren schicken das Formular der Vorschau ab. Trug es je Buchung
 * einen Parameter, scheiterte ein großer Auftrag am Parameterlimit des Containers — das sieht nur ein
 * Test mit laufendem Tomcat. Er schickt das Formular so ab, wie der Browser es täte: jedes benannte,
 * nicht gesperrte Feld, Checkboxen und Radios nur angehakt.
 *
 * <p>Die Daten der Rechnung kommen aus Mocks der Dienste, die {@code InvoiceService} befragt; eigene
 * H2-Datenbank wie in {@code RequestLogIntegrationTest}, weil ein zweiter Kontext die geteilte neu anlegte.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1499;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
class InvoiceFormParameterLimitIntegrationTest {

  private static final String ADMIN = "ipl";
  private static final long ORDER = 1L;
  /** More than the limit of the deployed environment (2000), let alone Tomcat's default (1000). */
  private static final int BOOKINGS = 2100;
  private static final LocalDate IN_SEPTEMBER = LocalDate.of(2026, 9, 15);

  private static final Pattern FORM = Pattern.compile("<form\\b[^>]*id=\"invoice-form\".*?</form>", Pattern.DOTALL);
  private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>");
  private static final Pattern SELECT = Pattern.compile("<select\\b([^>]*)>(.*?)</select>", Pattern.DOTALL);
  private static final Pattern OPTION = Pattern.compile("<option\\b([^>]*)>");
  private static final Pattern TEXTAREA = Pattern.compile("<textarea\\b([^>]*)>(.*?)</textarea>", Pattern.DOTALL);

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @MockitoBean
  private CustomerorderService customerorderService;
  @MockitoBean
  private SuborderService suborderService;
  @MockitoBean
  private TimereportService timereportService;
  @MockitoBean
  private BudgetQueryService budgetQueryService;

  @BeforeEach
  void seed() {
    admin();

    var customer = new Customer();
    customer.setName("Kunde A");
    customer.setAddress("Straße 1");
    var customerorder = new Customerorder();
    customerorder.setSign("ABC");
    customerorder.setDescription("Wartung und Betrieb");
    customerorder.setShortdescription("Wartung");
    customerorder.setCustomer(customer);
    setId(customerorder, ORDER);
    var branch = suborder("01", 10L, customerorder, null);
    var child = suborder("01", 11L, customerorder, branch);
    var other = suborder("02", 20L, customerorder, null);
    when(customerorderService.getCustomerorderById(ORDER)).thenReturn(customerorder);
    // the order the form offers, and with it the one it sends
    when(customerorderService.getInvoiceableCustomerorders()).thenReturn(List.of(new CustomerorderOption(
        ORDER, "ABC", "Wartung", "Wartung und Betrieb", "Kunde A", "Kunde A", false)));
    when(suborderService.getSubordersByCustomerorderId(ORDER)).thenReturn(List.of(branch, child, other));

    // every booking refers to two tickets, so the groupings by ticket list it twice
    when(timereportService.getTimereportsByDatesAndSuborderId(any(), any(), anyLong())).thenAnswer(invocation -> {
      long suborderId = invocation.getArgument(2);
      return LongStream.rangeClosed(1, BOOKINGS)
          .filter(id -> suborderOf(id) == suborderId)
          .mapToObj(id -> TimereportDTO.builder()
              .id(id).suborderId(suborderId)
              .employeeSign("pa").employeeName("Person A")
              .referenceday(IN_SEPTEMBER).duration(Duration.ofHours(1)).taskdescription("Arbeit")
              .ticketReferences(List.of("ABC-1", "ABC-2"))
              .build())
          .toList();
    });
  }

  @ParameterizedTest
  @EnumSource(InvoiceGrouping.class)
  void the_preview_of_a_large_order_can_be_printed(InvoiceGrouping grouping) throws Exception {
    var form = formOf(generate(grouping));

    var print = post("/invoice/print", form);

    assertThat(print.statusCode()).isEqualTo(200);
    assertThat(print.body()).contains(">" + BOOKINGS + ":00<");
  }

  @ParameterizedTest
  @EnumSource(InvoiceGrouping.class)
  void the_preview_of_a_large_order_can_be_exported(InvoiceGrouping grouping) throws Exception {
    var form = formOf(generate(grouping));

    var export = send(HttpRequest.newBuilder(uri("/invoice/export")).POST(BodyPublishers.ofString(encode(form)))
        .header("Content-Type", "application/x-www-form-urlencoded"), BodyHandlers.ofByteArray());

    assertThat(export.statusCode()).isEqualTo(200);
    assertThat(export.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("spreadsheetml"));
  }

  /** Without any deselection the form names no booking and no suborder at all. */
  @Test
  void the_form_names_no_booking_while_nothing_is_deselected() throws Exception {
    var form = formOf(generate(InvoiceGrouping.SUBORDER_TICKET));

    assertThat(form).hasSizeLessThan(100);
    assertThat(valueOf(form, "excludedTimereportIds")).isEmpty();
    assertThat(valueOf(form, "excludedSuborderIds")).isEmpty();
  }

  @Test
  void a_deselected_booking_and_suborder_are_left_out_of_the_print() throws Exception {
    var form = formOf(generate(InvoiceGrouping.SUBORDER));
    replace(form, "excludedTimereportIds", "1,2");
    replace(form, "excludedSuborderIds", "20");

    var print = post("/invoice/print", form);

    assertThat(print.statusCode()).isEqualTo(200);
    // suborder 20 holds every third booking: 700 of them; bookings 1 and 2 lie in the others
    assertThat(print.body()).contains(">" + (BOOKINGS - 700 - 2) + ":00<");
  }

  @Test
  void regrouping_keeps_the_deselection() throws Exception {
    var form = formOf(generate(InvoiceGrouping.SUBORDER));
    replace(form, "excludedTimereportIds", "1,2");
    replace(form, "excludedSuborderIds", "20");
    replace(form, "grouping", InvoiceGrouping.SUBORDER_TICKET.name());
    form.add(new SimpleEntry<>("regroup", "true"));

    var regrouped = formOf(post("/invoice/generate", form).body());

    assertThat(valueOf(regrouped, "excludedTimereportIds")).isEqualTo("1,2");
    assertThat(valueOf(regrouped, "excludedSuborderIds")).isEqualTo("20");
  }

  // --- fixture ---------------------------------------------------------------------------------

  /** The bookings go round the suborders: 10, 11, 20, 10, … */
  private static long suborderOf(long timereportId) {
    return switch ((int) (timereportId % 3)) {
      case 1 -> 10L;
      case 2 -> 11L;
      default -> 20L;
    };
  }

  private String generate(InvoiceGrouping grouping) throws Exception {
    var form = new ArrayList<Map.Entry<String, String>>(List.of(
        new SimpleEntry<>("orderId", String.valueOf(ORDER)),
        new SimpleEntry<>("invoiceview", GlobalConstants.VIEW_MONTHLY),
        new SimpleEntry<>("fromYearMonth", "2026-09"),
        new SimpleEntry<>("grouping", grouping.name())));
    var response = post("/invoice/generate", form);
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  /** What a browser submits of the form, in its order. */
  private static List<Map.Entry<String, String>> formOf(String page) {
    var matcher = FORM.matcher(page);
    assertThat(matcher.find()).as("the page has the invoice form").isTrue();
    var html = matcher.group();
    var fields = new ArrayList<Map.Entry<String, String>>();
    for (var input : matches(INPUT, html)) {
      var name = attribute(input, "name");
      var type = attribute(input, "type");
      if (name == null || input.contains(" disabled")) {
        continue;
      }
      boolean checkable = "checkbox".equals(type) || "radio".equals(type);
      if (checkable && !input.contains(" checked")) {
        continue;
      }
      var value = attribute(input, "value");
      fields.add(new SimpleEntry<>(name, value != null ? value : checkable ? "on" : ""));
    }
    Matcher select = SELECT.matcher(html);
    while (select.find()) {
      var name = attribute(select.group(1), "name");
      var options = matches(OPTION, select.group(2));
      if (name == null || options.isEmpty()) {
        continue;
      }
      var chosen = options.stream().filter(option -> option.contains(" selected")).findFirst().orElse(options.get(0));
      var value = attribute(chosen, "value");
      fields.add(new SimpleEntry<>(name, value != null ? value : ""));
    }
    Matcher textarea = TEXTAREA.matcher(html);
    while (textarea.find()) {
      var name = attribute(textarea.group(1), "name");
      if (name != null) {
        fields.add(new SimpleEntry<>(name, unescape(textarea.group(2))));
      }
    }
    return fields;
  }

  private static List<String> matches(Pattern pattern, String html) {
    var result = new ArrayList<String>();
    var matcher = pattern.matcher(html);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    return result;
  }

  private static String attribute(String tag, String name) {
    var matcher = Pattern.compile("\\s" + name + "=\"([^\"]*)\"").matcher(tag);
    return matcher.find() ? unescape(matcher.group(1)) : null;
  }

  private static String unescape(String html) {
    return html.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
  }

  private static String valueOf(List<Map.Entry<String, String>> form, String name) {
    return form.stream().filter(field -> field.getKey().equals(name)).map(Map.Entry::getValue).findFirst().orElse(null);
  }

  private static void replace(List<Map.Entry<String, String>> form, String name, String value) {
    form.removeIf(field -> field.getKey().equals(name));
    form.add(new SimpleEntry<>(name, value));
  }

  private HttpResponse<String> post(String path, List<Map.Entry<String, String>> form) throws Exception {
    return send(HttpRequest.newBuilder(uri(path)).POST(BodyPublishers.ofString(encode(form)))
        .header("Content-Type", "application/x-www-form-urlencoded"), BodyHandlers.ofString());
  }

  private static String encode(List<Map.Entry<String, String>> form) {
    return form.stream()
        .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
            + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path + "?login-name=" + ADMIN);
  }

  private static <T> HttpResponse<T> send(HttpRequest.Builder request, HttpResponse.BodyHandler<T> handler) throws Exception {
    return HttpClient.newHttpClient().send(request.build(), handler);
  }

  private static Suborder suborder(String sign, long id, Customerorder customerorder, Suborder parent) {
    var suborder = new Suborder();
    setId(suborder, id);
    suborder.setSign(sign);
    suborder.setDescription("Teilauftrag " + sign);
    suborder.setShortdescription(sign);
    suborder.setCustomerorder(customerorder);
    suborder.setParentorder(parent);
    suborder.setInvoice('Y');
    suborder.deriveCompleteOrderSign();
    return suborder;
  }

  /** An administrator is backoffice and needs no contract. */
  private void admin() {
    if (employeeRepository.findBySign(ADMIN).isPresent()) {
      return;
    }
    var salatUser = new SalatUser();
    salatUser.setLoginname(ADMIN);
    salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_ADM);
    salatUser = salatUserRepository.save(salatUser);
    var employee = new Employee();
    employee.setSign(ADMIN);
    employee.setFirstname("Person");
    employee.setLastname("A");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employeeRepository.save(employee);
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
