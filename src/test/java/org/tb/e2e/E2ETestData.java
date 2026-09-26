package org.tb.e2e;

import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.common.util.ClockProvider;
import org.tb.common.util.DateUtils;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.ReferencedayRepository;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.CustomerorderRepository;
import org.tb.order.persistence.EmployeeorderRepository;
import org.tb.order.persistence.SuborderRepository;

/**
 * Seeds a fixed, realistic set of master/reference data for the dailyreport E2E suite:
 * Customer, Customerorder, Suborder, Employee, Employeecontract, Employeeorder and a pair of
 * public holidays.
 *
 * <p>Bookings are seeded only for the people of the overview before a release (#760,
 * {@code ReleaseReviewE2ETest}): a period that can be reviewed needs every working day of it
 * booked or marked as not worked, and entering weeks of bookings through the UI would make that
 * test slow and fragile. They belong to people of their own, whose contracts no other test looks at.
 *
 * <p>The three "standard" suborders (Urlaub, Krankheit, Fortbildung) are deliberately left
 * without an Employeeorder here: they are marked {@code standard=true} so that logging in as
 * a seeded employee (via {@link org.tb.auth.configuration.LocalDevSecurityConfiguration})
 * provisions them automatically, exercising the same production mechanism end-to-end.
 */
@UtilityClass
public class E2ETestData {

  public static final String CUSTOMER_HBT_SHORTNAME = "HBT";

  public static final String SUBORDER_KRANKHEIT_SIGN = "Krankheit";
  public static final String CUSTOMERORDER_KRANK_SIGN = "KRANK";

  public static final String EMPLOYEE_MA_SIGN = "ema";
  public static final String EMPLOYEE_PV_SIGN = "epv";
  public static final String EMPLOYEE_BL_SIGN = "ebl";
  public static final String EMPLOYEE_BO_SIGN = "ebo";
  public static final String EMPLOYEE_RESTRICTED_SIGN = "ers";

  /**
   * A person without a single booking whose contract begins on Wednesday, 2026-06-10 — for the
   * dashboard hint on working days of the previous week without a booking (#1124): in the week of
   * 2026-06-15 it names exactly the 10th to the 12th. A person of its own, because what the other
   * seeded people have booked depends on which E2E classes ran before.
   */
  public static final String EMPLOYEE_WITHOUT_BOOKINGS_SIGN = "evw";
  public static final LocalDate WITHOUT_BOOKINGS_CONTRACT_START = LocalDate.of(2026, 6, 10);

  public static final String CUSTOMERORDER_CONTOSO_SIGN = "CONTOSO-01";
  public static final String SUBORDER_ALPHA_DEV_SIGN = "ALPHA-DEV";
  public static final String CUSTOMERORDER_GLOBEX_SIGN = "GLOBEX-01";
  public static final String SUBORDER_GLOBEX_CONSULT_SIGN = "GLOBEX-CONSULT";
  public static final String CUSTOMERORDER_INITECH_SIGN = "INITECH-01";
  public static final String SUBORDER_INITECH_SUPPORT_SIGN = "INITECH-SUPPORT";
  public static final String SUBORDER_INITECH_MIGRATION_SIGN = "INITECH-MIGRATION";

  /** Standby: booked like any other time, but no working time (#463). */
  public static final String CUSTOMERORDER_STANDBY_SIGN = "INITECH-02";
  public static final String SUBORDER_STANDBY_SIGN = "INITECH-RUFBEREITSCHAFT";

  /**
   * Public holidays for the daily view's target calculation (#857). Deliberately in October 2026,
   * a month no other E2E class looks at - a holiday changes the working time target of its whole
   * month, and the E2E database is shared.
   */
  public static final LocalDate HOLIDAY_ON_WEEKDAY = LocalDate.of(2026, 10, 5);
  public static final LocalDate HOLIDAY_ON_WEEKEND = LocalDate.of(2026, 10, 10);

  private static final LocalDate PAST = LocalDate.of(2020, 1, 1);

  /**
   * Already-released-through date for {@link #EMPLOYEE_MA_SIGN}'s contract, aligned to
   * {@code PlaywrightE2ETestBase.FIXED_NOW} (2026-06-15): only the following weekend
   * (2026-05-30/31) remains unreleased, so the self-release E2E test doesn't hit the
   * "all working days must be booked" business rule for the ~6 years since {@link #PAST}.
   */
  private static final LocalDate ALREADY_RELEASED_UNTIL = LocalDate.of(2026, 5, 29);

  /**
   * The person whose overview before a release is only looked at (#760), never changed: released
   * until the end of August, so {@code until=2026-10} shows September and October 2026. Her people
   * lead is {@link #EMPLOYEE_PV_SIGN}.
   */
  public static final String EMPLOYEE_REVIEWED_SIGN = "erv";
  public static final String EMPLOYEE_REVIEWED_NAME = "Vera Vorschau";
  public static final String REVIEWED_MONTH = "2026-10";
  /** The one working day of {@link #EMPLOYEE_REVIEWED_SIGN}'s period without a booking. */
  public static final LocalDate REVIEWED_DAY_WITHOUT_BOOKING = LocalDate.of(2026, 9, 4);
  /** A day booked for less than the daily working time — short days are no finding (#760). */
  public static final LocalDate REVIEWED_SHORT_DAY = LocalDate.of(2026, 9, 3);
  /** A day of {@link #EMPLOYEE_REVIEWED_SIGN}'s period marked as not worked. */
  public static final LocalDate REVIEWED_NOT_WORKED_DAY = LocalDate.of(2026, 9, 7);
  /** A comment over several lines, which the overview has to show in full. */
  public static final String REVIEWED_LONG_COMMENT = """
      Schnittstelle zum Abrechnungssystem angebunden und die Zuordnung der Belege geprüft.
      Fehlerbehandlung für abgelehnte Belege ergänzt und mit dem Team besprochen.
      Offene Punkte für die nächste Woche im Ticket festgehalten.""";
  private static final LocalDate REVIEWED_RELEASED_UNTIL = LocalDate.of(2026, 8, 31);

  /**
   * The person with an open booking before her period (#760), only looked at: released until the
   * end of August, but one booking of that day is still open, as data from before the release
   * marked every booking can be. The release sweeps it along, so {@code until=2026-09} shows it
   * apart from September, whose working days are all marked as not worked.
   */
  public static final String EMPLOYEE_STRAY_SIGN = "erb";
  public static final String EMPLOYEE_STRAY_NAME = "Berta Bestand";
  public static final String STRAY_MONTH = "2026-09";
  public static final LocalDate STRAY_DAY = LocalDate.of(2026, 8, 31);
  public static final String STRAY_COMMENT = "Offen geblieben vor der letzten Freigabe";

  /**
   * Released until the Sunday before, so {@code until=2026-11} shows the last week of November
   * 2026: two bookings, one working day without a booking, the rest not worked.
   */
  public static final String RELEASING_MONTH = "2026-11";
  public static final LocalDate RELEASING_DAY_WITHOUT_BOOKING = LocalDate.of(2026, 11, 25);
  public static final String RELEASING_EDITED_COMMENT = "Freigabe vorbereitet";
  private static final LocalDate RELEASING_RELEASED_UNTIL = LocalDate.of(2026, 11, 22);

  /**
   * The person who books the missing day and releases from the overview (#760), one per browser: a
   * period can be released only once, and without {@code -De2e.browsers} both browsers run against
   * the same database.
   */
  public static String releasingEmployeeSign(E2EBrowser browser) {
    return switch (browser) {
      case CHROME -> "erc";
      case FIREFOX -> "erf";
    };
  }

  private static String releasingEmployeeFirstname(E2EBrowser browser) {
    return switch (browser) {
      case CHROME -> "Clara";
      case FIREFOX -> "Flora";
    };
  }

  /**
   * Accepted until Thursday, 26.03.2026, and released until 31.03.2026, so {@code until=2026-03}
   * shows Friday, 27.03., to Tuesday, 31.03.2026: two released bookings, and the Friday without one.
   */
  public static final String ACCEPTING_MONTH = "2026-03";
  public static final LocalDate ACCEPTING_DAY_WITHOUT_BOOKING = LocalDate.of(2026, 3, 27);
  public static final String ACCEPTING_EDITED_COMMENT = "Datenübernahme vorbereitet";
  public static final String ACCEPTING_OTHER_COMMENT = "Abstimmung mit dem Kunden";
  private static final LocalDate ACCEPTING_ACCEPTED_UNTIL = LocalDate.of(2026, 3, 26);
  private static final LocalDate ACCEPTING_RELEASED_UNTIL = LocalDate.of(2026, 3, 31);

  /**
   * The person whose released bookings {@link #EMPLOYEE_PV_SIGN} reviews, corrects and accepts
   * (#1122), one per browser: a period can be accepted only once, and without
   * {@code -De2e.browsers} both browsers run against the same database.
   */
  public static String acceptedEmployeeSign(E2EBrowser browser) {
    return switch (browser) {
      case CHROME -> "eac";
      case FIREFOX -> "eaf";
    };
  }

  public static String acceptedEmployeeName(E2EBrowser browser) {
    return acceptedEmployeeFirstname(browser) + " Abnahme";
  }

  private static String acceptedEmployeeFirstname(E2EBrowser browser) {
    return switch (browser) {
      case CHROME -> "Carla";
      case FIREFOX -> "Fenja";
    };
  }

  public static void seedIfNeeded(
      CustomerRepository customerRepository,
      CustomerorderRepository customerorderRepository,
      SuborderRepository suborderRepository,
      EmployeeRepository employeeRepository,
      EmployeecontractRepository employeecontractRepository,
      EmployeeorderRepository employeeorderRepository,
      SalatUserRepository salatUserRepository,
      PublicholidayRepository publicholidayRepository,
      ReferencedayRepository referencedayRepository,
      TimereportRepository timereportRepository,
      WorkingdayRepository workingdayRepository) {

    if (customerRepository.findAllVisibleOrderByShortnameIgnoreCase().stream()
        .anyMatch(c -> CUSTOMER_HBT_SHORTNAME.equalsIgnoreCase(c.getShortname()))) {
      return;
    }

    publicholidayRepository.save(new Publicholiday(HOLIDAY_ON_WEEKDAY, "E2E-Feiertag am Werktag"));
    publicholidayRepository.save(new Publicholiday(HOLIDAY_ON_WEEKEND, "E2E-Feiertag am Wochenende"));

    Customer hbt = customer(customerRepository, "HBT GmbH", CUSTOMER_HBT_SHORTNAME);

    // --- Standard/absence orders (all customer HBT, all suborders standard=true) ---
    Customerorder vacationOrder = customerorder(customerorderRepository, hbt,
        GlobalConstants.CUSTOMERORDER_SIGN_VACATION, "Urlaub", OrderType.KRANK_URLAUB_ABWESEND);
    String currentYear = String.valueOf(Year.now(ClockProvider.getClock()).getValue());
    suborder(suborderRepository, vacationOrder, currentYear, "Urlaub " + currentYear,
        LocalDate.of(Integer.parseInt(currentYear), 1, 1), true, false);

    Customerorder sickOrder = customerorder(customerorderRepository, hbt,
        CUSTOMERORDER_KRANK_SIGN, "Krankheit", OrderType.KRANK_URLAUB_ABWESEND);
    suborder(suborderRepository, sickOrder, SUBORDER_KRANKHEIT_SIGN, "Krankheit", PAST, true, false);

    Customerorder trainingOrder = customerorder(customerorderRepository, hbt,
        GlobalConstants.CUSTOMERORDER_SIGN_TRAINING, "Fortbildung", OrderType.STANDARD);
    suborder(suborderRepository, trainingOrder, GlobalConstants.SUBRORDER_SIGN_TRAINING, "Fortbildung",
        PAST, true, true);

    // --- Project master data (richer scenario) ---
    Customer contoso = customer(customerRepository, "Contoso AG", "CONTOSO");
    Customerorder contosoOrder = customerorder(customerorderRepository, contoso,
        CUSTOMERORDER_CONTOSO_SIGN, "Projekt Alpha", OrderType.STANDARD);
    Suborder alphaDev = suborder(suborderRepository, contosoOrder, SUBORDER_ALPHA_DEV_SIGN, "Entwicklung",
        PAST, false, false);

    Customer globex = customer(customerRepository, "Globex GmbH", "GLOBEX");
    Customerorder globexOrder = customerorder(customerorderRepository, globex,
        CUSTOMERORDER_GLOBEX_SIGN, "Beratung", OrderType.STANDARD);
    Suborder globexConsult = suborder(suborderRepository, globexOrder, SUBORDER_GLOBEX_CONSULT_SIGN, "Consulting",
        PAST, false, false);

    Customer initech = customer(customerRepository, "Initech KG", "INITECH");
    Customerorder initechOrder = customerorder(customerorderRepository, initech,
        CUSTOMERORDER_INITECH_SIGN, "Support & Migration", OrderType.STANDARD);
    suborder(suborderRepository, initechOrder, SUBORDER_INITECH_SUPPORT_SIGN, "Support", PAST, false, false);
    suborder(suborderRepository, initechOrder, SUBORDER_INITECH_MIGRATION_SIGN, "Migration", PAST, false, false);

    Customerorder standbyOrder = customerorder(customerorderRepository, initech,
        CUSTOMERORDER_STANDBY_SIGN, "Rufbereitschaft", OrderType.BEREITSCHAFT);
    Suborder standby = suborder(suborderRepository, standbyOrder, SUBORDER_STANDBY_SIGN, "Rufbereitschaft",
        PAST, false, false);

    // --- Employees across roles ---
    Employee peopleLead = employee(employeeRepository, salatUserRepository, EMPLOYEE_PV_SIGN,
        "Petra", "Vorgesetzte", GlobalConstants.EMPLOYEE_STATUS_PV);
    Employee manager = employee(employeeRepository, salatUserRepository, EMPLOYEE_BL_SIGN,
        "Bernd", "Leitmann", GlobalConstants.EMPLOYEE_STATUS_BL);
    Employee backoffice = employee(employeeRepository, salatUserRepository, EMPLOYEE_BO_SIGN,
        "Beate", "Officeva", GlobalConstants.EMPLOYEE_STATUS_BO);
    Employee restricted = employee(employeeRepository, salatUserRepository, EMPLOYEE_RESTRICTED_SIGN,
        "Rita", "Strictedt", GlobalConstants.EMPLOYEE_STATUS_RESTRICTED);
    Employee regular = employee(employeeRepository, salatUserRepository, EMPLOYEE_MA_SIGN,
        "Manuela", "Angestellt", GlobalConstants.EMPLOYEE_STATUS_MA);
    Employee withoutBookings = employee(employeeRepository, salatUserRepository, EMPLOYEE_WITHOUT_BOOKINGS_SIGN,
        "Vera", "Vorwoche", GlobalConstants.EMPLOYEE_STATUS_MA);

    employeecontract(employeecontractRepository, peopleLead, null);
    employeecontract(employeecontractRepository, manager, null);
    employeecontract(employeecontractRepository, backoffice, null);
    employeecontract(employeecontractRepository, restricted, null);
    Employeecontract regularContract = employeecontract(employeecontractRepository, regular, peopleLead);
    regularContract.setReportReleaseDate(ALREADY_RELEASED_UNTIL);
    regularContract.setVacationEntitlement(GlobalConstants.DEFAULT_VACATION_PER_YEAR);
    regularContract = employeecontractRepository.save(regularContract);

    employeeorder(employeeorderRepository, regularContract, alphaDev);
    employeeorder(employeeorderRepository, regularContract, globexConsult);
    employeeorder(employeeorderRepository, regularContract, standby);

    Employeecontract withoutBookingsContract = employeecontract(employeecontractRepository, withoutBookings, null);
    withoutBookingsContract.setValidFrom(WITHOUT_BOOKINGS_CONTRACT_START);
    employeecontractRepository.save(withoutBookingsContract);

    // --- The overview before a release (#760): one person to look at, one per browser to release ---
    var bookings = new Bookings(referencedayRepository, timereportRepository, workingdayRepository);

    Employee reviewed = employee(employeeRepository, salatUserRepository, EMPLOYEE_REVIEWED_SIGN,
        "Vera", "Vorschau", GlobalConstants.EMPLOYEE_STATUS_MA);
    Employeecontract reviewedContract = employeecontract(employeecontractRepository, reviewed, peopleLead);
    reviewedContract.setReportReleaseDate(REVIEWED_RELEASED_UNTIL);
    reviewedContract = employeecontractRepository.save(reviewedContract);
    Employeeorder reviewedAlpha = employeeorder(employeeorderRepository, reviewedContract, alphaDev);
    Employeeorder reviewedGlobex = employeeorder(employeeorderRepository, reviewedContract, globexConsult);
    bookings.book(reviewedAlpha, LocalDate.of(2026, 9, 1), Duration.ofHours(4),
        "Anforderungen mit dem Fachbereich abgestimmt");
    bookings.book(reviewedGlobex, LocalDate.of(2026, 9, 1), Duration.ofHours(2), "Workshop vorbereitet");
    bookings.book(reviewedAlpha, LocalDate.of(2026, 9, 2), Duration.ofHours(6), REVIEWED_LONG_COMMENT);
    bookings.book(reviewedAlpha, REVIEWED_SHORT_DAY, Duration.ofHours(3), "Code-Review");
    bookings.book(reviewedGlobex, LocalDate.of(2026, 10, 6), Duration.ofHours(5), "Abstimmung nach dem Feiertag");
    bookings.notWorkedExcept(reviewedContract, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31),
        Set.of(REVIEWED_DAY_WITHOUT_BOOKING));

    Employee stray = employee(employeeRepository, salatUserRepository, EMPLOYEE_STRAY_SIGN,
        "Berta", "Bestand", GlobalConstants.EMPLOYEE_STATUS_MA);
    Employeecontract strayContract = employeecontract(employeecontractRepository, stray, peopleLead);
    strayContract.setReportReleaseDate(STRAY_DAY);
    strayContract = employeecontractRepository.save(strayContract);
    Employeeorder strayAlpha = employeeorder(employeeorderRepository, strayContract, alphaDev);
    bookings.book(strayAlpha, STRAY_DAY, Duration.ofHours(2), STRAY_COMMENT);
    bookings.notWorkedExcept(strayContract, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Set.of());

    for (E2EBrowser browser : E2EBrowser.values()) {
      Employee releasing = employee(employeeRepository, salatUserRepository, releasingEmployeeSign(browser),
          releasingEmployeeFirstname(browser), "Freigabe", GlobalConstants.EMPLOYEE_STATUS_MA);
      Employeecontract releasingContract = employeecontract(employeecontractRepository, releasing, peopleLead);
      releasingContract.setReportReleaseDate(RELEASING_RELEASED_UNTIL);
      releasingContract = employeecontractRepository.save(releasingContract);
      Employeeorder releasingAlpha = employeeorder(employeeorderRepository, releasingContract, alphaDev);
      bookings.book(releasingAlpha, LocalDate.of(2026, 11, 23), Duration.ofHours(5), RELEASING_EDITED_COMMENT);
      bookings.book(releasingAlpha, LocalDate.of(2026, 11, 24), Duration.ofHours(4), "Tests ergänzt");
      bookings.notWorkedExcept(releasingContract, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30),
          Set.of(RELEASING_DAY_WITHOUT_BOOKING));
    }

    // --- The overview before an acceptance (#1122): released bookings, one person per browser ---
    for (E2EBrowser browser : E2EBrowser.values()) {
      Employee accepted = employee(employeeRepository, salatUserRepository, acceptedEmployeeSign(browser),
          acceptedEmployeeFirstname(browser), "Abnahme", GlobalConstants.EMPLOYEE_STATUS_MA);
      Employeecontract acceptedContract = employeecontract(employeecontractRepository, accepted, peopleLead);
      acceptedContract.setReportReleaseDate(ACCEPTING_RELEASED_UNTIL);
      acceptedContract.setReportAcceptanceDate(ACCEPTING_ACCEPTED_UNTIL);
      acceptedContract = employeecontractRepository.save(acceptedContract);
      Employeeorder acceptedAlpha = employeeorder(employeeorderRepository, acceptedContract, alphaDev);
      Employeeorder acceptedGlobex = employeeorder(employeeorderRepository, acceptedContract, globexConsult);
      bookings.book(acceptedAlpha, LocalDate.of(2026, 3, 30), Duration.ofHours(8), ACCEPTING_EDITED_COMMENT,
          GlobalConstants.TIMEREPORT_STATUS_COMMITED);
      bookings.book(acceptedGlobex, LocalDate.of(2026, 3, 31), Duration.ofHours(6), ACCEPTING_OTHER_COMMENT,
          GlobalConstants.TIMEREPORT_STATUS_COMMITED);
    }
  }

  private static Customer customer(CustomerRepository repository, String name, String shortname) {
    Customer customer = new Customer();
    customer.setName(name);
    customer.setShortname(shortname);
    customer.setAddress("Musterstraße 1, 12345 Musterstadt");
    return repository.save(customer);
  }

  private static Customerorder customerorder(CustomerorderRepository repository, Customer customer,
      String sign, String description, OrderType orderType) {
    Customerorder customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription(description);
    customerorder.setFromDate(PAST);
    customerorder.setOrderType(orderType);
    customerorder.setDebithours(Duration.ZERO);
    return repository.save(customerorder);
  }

  private static Suborder suborder(SuborderRepository repository, Customerorder customerorder,
      String sign, String description, LocalDate fromDate, boolean standard, boolean training) {
    Suborder suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(description);
    suborder.setFromDate(fromDate);
    suborder.setStandard(standard);
    suborder.setTrainingFlag(training);
    suborder.setDebithours(Duration.ZERO);
    return repository.save(suborder);
  }

  private static Employee employee(EmployeeRepository employeeRepository, SalatUserRepository salatUserRepository,
      String sign, String firstname, String lastname, String status) {
    SalatUser salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(status);
    salatUser = salatUserRepository.save(salatUser);

    Employee employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname(firstname);
    employee.setLastname(lastname);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    return employeeRepository.save(employee);
  }

  private static Employeecontract employeecontract(EmployeecontractRepository repository, Employee employee,
      Employee supervisor) {
    Employeecontract contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(PAST);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    if (supervisor != null) {
      contract.setSupervisors(java.util.List.of(supervisor));
    }
    return repository.save(contract);
  }

  /**
   * Bookings straight into the database, as {@code TimereportService} would store them: open unless
   * a status is given, on the shared reference day of their date, numbered per day, and with a
   * working day that starts at nine — a project booking needs a start of work, otherwise the check
   * before a release reports it.
   */
  private static final class Bookings {

    private final ReferencedayRepository referencedays;
    private final TimereportRepository timereports;
    private final WorkingdayRepository workingdays;
    private final Map<String, Integer> bookingsPerDay = new HashMap<>();

    private Bookings(ReferencedayRepository referencedays, TimereportRepository timereports,
        WorkingdayRepository workingdays) {
      this.referencedays = referencedays;
      this.timereports = timereports;
      this.workingdays = workingdays;
    }

    void book(Employeeorder employeeorder, LocalDate day, Duration duration, String comment) {
      book(employeeorder, day, duration, comment, GlobalConstants.TIMEREPORT_STATUS_OPEN);
    }

    void book(Employeeorder employeeorder, LocalDate day, Duration duration, String comment, String status) {
      var contract = employeeorder.getEmployeecontract();
      if (workingdays.findByRefdayAndEmployeecontractId(day, contract.getId()).isEmpty()) {
        workingday(contract, day, WORKED);
      }
      int sequence = bookingsPerDay.merge(contract.getId() + "@" + day, 1, Integer::sum);

      var timereport = new Timereport();
      timereport.setReferenceday(referenceday(day));
      timereport.setEmployeecontract(contract);
      timereport.setEmployeeorder(employeeorder);
      timereport.setSuborder(employeeorder.getSuborder());
      timereport.setDurationhours((int) duration.toHours());
      timereport.setDurationminutes(duration.toMinutesPart());
      timereport.setTaskdescription(comment);
      timereport.setStatus(status);
      timereport.setTraining(false);
      timereport.setSequencenumber(sequence);
      timereports.save(timereport);
    }

    /**
     * Marks every weekday from {@code from} to {@code until} as not worked, apart from the public
     * holiday on a weekday, the days in {@code except} and the days that already have a working day.
     */
    void notWorkedExcept(Employeecontract contract, LocalDate from, LocalDate until, Set<LocalDate> except) {
      from.datesUntil(until.plusDays(1))
          .filter(DateUtils::isWeekday)
          .filter(day -> !day.equals(HOLIDAY_ON_WEEKDAY))
          .filter(day -> !except.contains(day))
          .filter(day -> workingdays.findByRefdayAndEmployeecontractId(day, contract.getId()).isEmpty())
          .forEach(day -> workingday(contract, day, NOT_WORKED));
    }

    private void workingday(Employeecontract contract, LocalDate day, WorkingDayType type) {
      var workingday = new Workingday();
      workingday.setEmployeecontract(contract);
      workingday.setRefday(day);
      workingday.setType(type);
      if (type == WORKED) {
        workingday.setStarttimehour(9);
      }
      workingdays.save(workingday);
    }

    /** The reference day every booking of that date shares, created like {@code TimereportService} does. */
    private Referenceday referenceday(LocalDate day) {
      return referencedays.findByRefdate(day).orElseGet(() -> {
        var referenceday = new Referenceday();
        referenceday.setRefdate(day);
        referenceday.setDow(DateUtils.getDoW(day));
        referenceday.setHoliday(false);
        referenceday.setName("");
        referenceday.setWorkingday(DateUtils.isWeekday(day));
        return referencedays.save(referenceday);
      });
    }
  }

  private static Employeeorder employeeorder(EmployeeorderRepository repository, Employeecontract contract,
      Suborder suborder) {
    Employeeorder employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setSign(" ");
    employeeorder.setFromDate(PAST);
    employeeorder.setDebithours(Duration.ZERO);
    return repository.save(employeeorder);
  }

}
