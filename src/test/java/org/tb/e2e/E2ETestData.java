package org.tb.e2e;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import lombok.experimental.UtilityClass;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.common.util.ClockProvider;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Vacation;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.employee.persistence.VacationRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.CustomerorderRepository;
import org.tb.order.persistence.EmployeeorderRepository;
import org.tb.order.persistence.SuborderRepository;

/**
 * Seeds a fixed, realistic set of master/reference data for the dailyreport E2E suite:
 * Customer, Customerorder, Suborder, Employee, Employeecontract and Employeeorder.
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

  public static final String CUSTOMERORDER_CONTOSO_SIGN = "CONTOSO-01";
  public static final String SUBORDER_ALPHA_DEV_SIGN = "ALPHA-DEV";
  public static final String CUSTOMERORDER_GLOBEX_SIGN = "GLOBEX-01";
  public static final String SUBORDER_GLOBEX_CONSULT_SIGN = "GLOBEX-CONSULT";
  public static final String CUSTOMERORDER_INITECH_SIGN = "INITECH-01";
  public static final String SUBORDER_INITECH_SUPPORT_SIGN = "INITECH-SUPPORT";
  public static final String SUBORDER_INITECH_MIGRATION_SIGN = "INITECH-MIGRATION";

  private static final LocalDate PAST = LocalDate.of(2020, 1, 1);

  /**
   * Already-released-through date for {@link #EMPLOYEE_MA_SIGN}'s contract, aligned to
   * {@code PlaywrightE2ETestBase.FIXED_NOW} (2026-06-15): only the following weekend
   * (2026-05-30/31) remains unreleased, so the self-release E2E test doesn't hit the
   * "all working days must be booked" business rule for the ~6 years since {@link #PAST}.
   */
  private static final LocalDate ALREADY_RELEASED_UNTIL = LocalDate.of(2026, 5, 29);

  public static void seedIfNeeded(
      CustomerRepository customerRepository,
      CustomerorderRepository customerorderRepository,
      SuborderRepository suborderRepository,
      EmployeeRepository employeeRepository,
      EmployeecontractRepository employeecontractRepository,
      EmployeeorderRepository employeeorderRepository,
      SalatUserRepository salatUserRepository,
      VacationRepository vacationRepository) {

    if (customerRepository.findAllVisible().stream()
        .anyMatch(c -> CUSTOMER_HBT_SHORTNAME.equalsIgnoreCase(c.getShortname()))) {
      return;
    }

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

    employeecontract(employeecontractRepository, peopleLead, null);
    employeecontract(employeecontractRepository, manager, null);
    employeecontract(employeecontractRepository, backoffice, null);
    employeecontract(employeecontractRepository, restricted, null);
    Employeecontract regularContract = employeecontract(employeecontractRepository, regular, peopleLead);
    regularContract.setReportReleaseDate(ALREADY_RELEASED_UNTIL);
    regularContract = employeecontractRepository.save(regularContract);

    employeeorder(employeeorderRepository, regularContract, alphaDev);
    employeeorder(employeeorderRepository, regularContract, globexConsult);

    Vacation vacation = new Vacation();
    vacation.setEmployeecontract(regularContract);
    vacation.setYear(Year.now(ClockProvider.getClock()).getValue());
    vacation.setEntitlement(GlobalConstants.DEFAULT_VACATION_PER_YEAR);
    vacation.setUsed(0);
    vacationRepository.save(vacation);
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
