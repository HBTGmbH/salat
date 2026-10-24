package de.hbt.salat.employee.service;

import static de.hbt.salat.auth.domain.AccessLevel.LOGIN;
import static de.hbt.salat.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.AA_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.EM_ANONYMIZE_WRONG_SIGN;
import static de.hbt.salat.common.exception.ErrorCode.EM_DELETE_GOT_VETO;
import static de.hbt.salat.common.exception.ErrorCode.EM_NOT_FOUND;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.SalatUserService;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.employee.auth.EmployeeAuthorization;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.EmployeeData;
import de.hbt.salat.employee.domain.EmployeeLogin;
import de.hbt.salat.employee.domain.EmployeeName;
import de.hbt.salat.employee.event.EmployeeAnonymizedEvent;
import de.hbt.salat.employee.event.EmployeeDeleteEvent;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.persistence.SalatUserReferences;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeeService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeeRepository employeeRepository;
  private final SalatUserService salatUserService;
  private final SalatUserReferences salatUserReferences;
  private final EmployeeDAO employeeDAO;
  private final AuthorizedUser authorizedUser;
  private final EmployeeAuthorization employeeAuthorization;

  public Employee getEmployeeByLoginname(String loginname) {
    return employeeDAO.getLoginEmployee(loginname);
  }

  public Employee getLoginEmployee() {
    var loginEmployee = employeeDAO.getLoginEmployee(authorizedUser.getEffectiveLoginSign());
    var allowedLoginEmployees = getLoginEmployees();
    if(!allowedLoginEmployees.contains(loginEmployee)) {
      log.warn("User {} tried to impersonate {}. This was not authorized!", authorizedUser.getLoginSign(), authorizedUser.getImpersonateLoginSign());
      throw new AuthorizationException(AA_REQUIRED);
    }
    return loginEmployee;
  }

  public List<Employee> getLoginEmployees() {
    return employeeRepository.findAllWithSalatUser().stream()
        .filter(e -> employeeAuthorization.isAuthorized(e, LOGIN))
        .toList();
  }

  public List<Employee> getAllEmployees() {
    return employeeDAO.getEmployees();
  }

  /**
   * The employees with these ids, without the read rules of this module (#1092).
   *
   * <p>Deliberately not {@link #getAllEmployees()}, which answers "whom may this user look at" and would be the wrong
   * question here: the caller already knows it may show these people, because it derived the ids from bookings the
   * user is allowed to read. A name that must not appear never comes out of that query, and filtering it a second
   * time against a different rule would drop names from a list whose rows are shown anyway.
   */
  public List<Employee> getEmployeesByIds(Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return employeeDAO.getEmployeesByIds(ids);
  }

  /**
   * The employees signed in under these logins, by the id of the login — hidden ones included, without the read rules
   * of this module (#1204). For naming the people an authorization rule refers to: whether somebody is hidden or may
   * be looked at has no bearing on whom a stored rule names, as with {@link #getAllEmployeeSigns()}.
   */
  public List<Employee> getEmployeesBySalatUserIds(Collection<Long> salatUserIds) {
    if (salatUserIds.isEmpty()) return List.of();
    return employeeRepository.findBySalatUserIds(salatUserIds);
  }

  /**
   * The logins of these employees as values (#1501), for a module that notifies them: a
   * notification goes to the login. An employee without a login is left out. Management only — the
   * nightly job of the achievements runs as one.
   */
  @Authorized(requiresManager = true)
  @Transactional(readOnly = true)
  public List<EmployeeLogin> getLoginsOfEmployees(Collection<Long> employeeIds) {
    if (employeeIds.isEmpty()) return List.of();
    return employeeRepository.findLoginsByIdIn(employeeIds);
  }

  /** {@link #getLoginsOfEmployees(Collection)} by the ids of the logins (#1501). */
  @Authorized(requiresManager = true)
  @Transactional(readOnly = true)
  public List<EmployeeLogin> getLoginsBySalatUserIds(Collection<Long> salatUserIds) {
    if (salatUserIds.isEmpty()) return List.of();
    return employeeRepository.findLoginsBySalatUserIdIn(salatUserIds);
  }

  /**
   * The name of the person signed in under each of these logins, by the id of the login (#1523) — for a
   * module that knows people by their login only and shows them by name, as the personal report groups
   * do. Hidden people included: the name says whose a record is, not whether the person is listed. A
   * login without an employee is left out.
   */
  @Transactional(readOnly = true)
  public Map<Long, String> getNamesBySalatUserIds(Collection<Long> salatUserIds) {
    if (salatUserIds.isEmpty()) return Map.of();
    return employeeRepository.findNamesBySalatUserIdIn(salatUserIds).stream()
        .collect(Collectors.toMap(EmployeeName::salatUserId, EmployeeName::name, (a, b) -> a));
  }

  /**
   * The employees offered in a select box: everything not hidden, plus the one with the id
   * {@code keepId} even if it is hidden (#956). Hiding an employee is a decluttering aid for
   * exactly these lists — it must not make an existing record uneditable.
   */
  public List<Employee> getSelectableEmployees(Long keepId) {
    return employeeDAO.getSelectableEmployees(keepId);
  }

  public List<Employee> getEmployeesWithContracts() {
    return employeeDAO.getEmployeesWithContracts();
  }

  public Employee getEmployeeBySign(String sign) {
    return employeeDAO.getEmployeeBySign(sign);
  }

  /**
   * Whether another login than the person's own already carries this name (#1333). Asked of the
   * logins, not of the people: an administrator has a login without being a person. Compared
   * exactly, as the unique key of the column does.
   *
   * @param employeeId the person being edited, {@code null} for a new one
   */
  @Transactional(readOnly = true)
  public boolean isLoginnameTaken(String loginname, Long employeeId) {
    var holderId = salatUserService.getSalatUserIdByLoginname(loginname).orElse(null);
    if (holderId == null) {
      return false;
    }
    var own = employeeId == null ? null : employeeDAO.getEmployeeById(employeeId);
    return own == null || own.getSalatUser() == null || !holderId.equals(own.getSalatUser().getId());
  }

  /**
   * Every sign that exists, for deciding whether a record referencing one still resolves (#966).
   *
   * <p>Deliberately not {@link #getAllEmployees()}: that one leaves out hidden employees and
   * everyone the viewer may not read. Both are display concerns, and neither has any bearing on
   * whether a stored sign resolves — a rate on a hidden person applies exactly as before (#956),
   * and whether it does must not depend on who is looking.
   */
  public Set<String> getAllEmployeeSigns() {
    return Set.copyOf(employeeRepository.findAllSigns());
  }

  public Employee getEmployeeById(long employeeId) {
    return employeeDAO.getEmployeeById(employeeId);
  }

  /**
   * Whether a person carries this id — for a module that refers to a person by id and refuses an
   * unknown one before the foreign key does (#1399, ADR-0036). Without the read rules, like
   * {@link #getSignsByIds}: the answer decides whether a reference resolves, not what the caller may
   * show.
   */
  @Transactional(readOnly = true)
  public boolean existsEmployeeById(long employeeId) {
    return employeeRepository.existsById(employeeId);
  }

  /**
   * The current sign of each of these employees, by id — for records that reference a person by
   * id and show the sign (#968). One statement for a whole list, and without the read rules, for
   * the reason given at {@link #getEmployeesByIds}: the caller shows the sign of a record it may
   * show, and the sign is how the person is named there.
   */
  public Map<Long, String> getSignsByIds(Collection<Long> ids) {
    return getEmployeesByIds(ids).stream()
        .filter(employee -> employee.getSign() != null)
        .collect(Collectors.toMap(Employee::getId, Employee::getSign));
  }

  public List<Employee> getEmployeesWithValidContracts() {
    return employeeDAO.getEmployeesWithValidContracts();
  }

  public List<Employee> getEligibleSupervisors() {
    return employeeDAO.getEmployeesWithValidContracts().stream()
        .filter(e -> {
          var status = e.getSalatUser().getStatus();
          return EMPLOYEE_STATUS_PV.equals(status) || EMPLOYEE_STATUS_BL.equals(status);
        })
        .toList();
  }

  public List<Employee> getEmployeesByFilter(String filter, Boolean showHidden) {
    return employeeDAO.getEmployeesByFilter(filter, showHidden);
  }

  public Employee getEmployeeForView(long id) {
    var employee = employeeDAO.getEmployeeById(id);
    if (employee == null) return null;
    if (!employeeAuthorization.isAuthorized(employee, AccessLevel.READ, employeeDAO.getActiveTeamEmployeeIds())) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return employee;
  }

  @Authorized(requiresManager = true)
  public Employee toggleHide(long id) {
    if (!authorizedUser.isManager()) throw new AuthorizationException(AA_NEEDS_MANAGER);
    Employee employee = employeeDAO.getEmployeeById(id);
    if (employee == null) throw new InvalidDataException(EM_NOT_FOUND);
    employee.setHide(!employee.getHide());
    employeeRepository.save(employee);
    return employee;
  }

  @Authorized(requiresManager = true)
  public void deleteEmployeeById(long employeeId) {
    var employee = employeeDAO.getEmployeeById(employeeId);
    if (employee == null) throw new InvalidDataException(EM_NOT_FOUND);

    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.DELETE)) {
      throw new RuntimeException("Illegal access to delete " + employeeId + " by " + authorizedUser.getLoginSign());
    }

    if(!employee.isNew()) {
      var event = new EmployeeDeleteEvent(employee.getId());
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            EM_DELETE_GOT_VETO,
            employee.getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }
    
    // Delete the employee first (this will remove the join table entry due to cascade)
    employeeRepository.deleteById(employeeId);
    
    // Delete the associated login if it exists; the auth module owns it (#1460)
    if (employee.getSalatUser() != null) {
      salatUserService.deleteSalatUser(employee.getSalatUser().getId());
    }
  }

  @Authorized(requiresManager = true)
  public void anonymizeEmployee(long employeeId, String confirmSign) {
    if (!authorizedUser.isManager()) throw new AuthorizationException(AA_NEEDS_MANAGER);
    var employee = employeeDAO.getEmployeeById(employeeId);
    if (employee == null) throw new InvalidDataException(EM_NOT_FOUND);
    if (!employee.getSign().equals(confirmSign)) {
      throw new InvalidDataException(EM_ANONYMIZE_WRONG_SIGN, employee.getSign());
    }
    employee.setFirstname("Anonymized");
    employee.setLastname("User");
    employee.setSign(anonymousSign(employeeId));
    employee.setHide(true);
    eventPublisher.publishEvent(new EmployeeAnonymizedEvent(employeeId));
    if (employee.getSalatUser() != null) {
      salatUserService.renameSalatUser(employee.getSalatUser().getId(), anonymousLoginname(employeeId));
    }
    employeeRepository.save(employee);
  }

  /**
   * The sign an anonymized employee carries from then on (#966).
   *
   * <p>It is deliberately longer than {@link GlobalConstants#EMPLOYEE_SIGN_MAX_LENGTH} and therefore
   * a value the employee form cannot produce, and it carries the id and is therefore unique among
   * pseudonyms. Together that makes it collide with no sign in use.
   *
   * <p>The previous form was the id in base 36, which is two characters wide for the usual range of
   * ids — the shape of an ordinary sign and free to collide with one. A collision would have handed
   * the cost assignments of the anonymized person to whoever carries that sign, which is worse than
   * losing them: the work would have been costed, only against the wrong person.
   */
  private static String anonymousSign(long employeeId) {
    return "ANON-" + employeeId;
  }

  /** The login name follows the sign, for the same reason and with the same guarantee. */
  private static String anonymousLoginname(long employeeId) {
    return "anon-" + employeeId;
  }

  /**
   * Saves a person from the values of the employee form, with their login (#1460). The login belongs
   * to the auth module: it creates or changes it from the values, and the person refers to it by id.
   */
  @Authorized(requiresManager = true)
  public void store(EmployeeData data) {
    var employee = data.id() == null ? new Employee() : employeeDAO.getEmployeeById(data.id());
    if (employee == null) {
      throw new InvalidDataException(EM_NOT_FOUND);
    }
    employee.setFirstname(data.firstname());
    employee.setLastname(data.lastname());
    employee.setSign(data.sign());
    employee.setGender(data.gender());
    employee.setHide(data.hide());
    checkWriteAllowed(employee);
    if (employee.getSalatUser() == null) {
      employee.setSalatUser(salatUserReferences.salatUser(
          salatUserService.createSalatUser(data.loginname(), data.status())));
    } else {
      salatUserService.updateSalatUser(employee.getSalatUser().getId(), data.loginname(), data.status());
    }
    employeeRepository.save(employee);
  }

  /**
   * Saves the person. A login that is not stored yet is read for its values and created by the auth
   * module (#1460); a stored one is only referred to, never written from here.
   */
  @Authorized(requiresManager = true)
  public void createOrUpdate(Employee employee) {
    checkWriteAllowed(employee);
    var login = employee.getSalatUser();
    if (login != null && login.getId() == null) {
      employee.setSalatUser(salatUserReferences.salatUser(
          salatUserService.createSalatUser(login.getLoginname(), login.getStatus())));
    }
    employeeRepository.save(employee);
  }

  private void checkWriteAllowed(Employee employee) {
    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.WRITE)) {
      throw new RuntimeException("Illegal access to save " + employee.getId() + " by " + authorizedUser.getLoginSign());
    }
  }
}
