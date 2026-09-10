package org.tb.employee.service;

import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.AA_REQUIRED;
import static org.tb.common.exception.ErrorCode.EM_ANONYMIZE_WRONG_SIGN;
import static org.tb.common.exception.ErrorCode.EM_DELETE_GOT_VETO;
import static org.tb.common.exception.ErrorCode.EM_NOT_FOUND;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.event.EmployeeAnonymizedEvent;
import org.tb.employee.event.EmployeeDeleteEvent;
import org.tb.employee.event.EmployeeSignChangedEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeeRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeeService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeeRepository employeeRepository;
  private final SalatUserRepository salatUserRepository;
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
   * The employees offered in a select box: everything not hidden, plus the one carrying
   * {@code keepSign} even if it is hidden (#956). Hiding an employee is a decluttering aid for
   * exactly these lists — it must not make an existing record uneditable.
   */
  public List<Employee> getSelectableEmployees(String keepSign) {
    return employeeDAO.getSelectableEmployees(keepSign);
  }

  public List<Employee> getEmployeesWithContracts() {
    return employeeDAO.getEmployeesWithContracts();
  }

  public Employee getEmployeeBySign(String sign) {
    return employeeDAO.getEmployeeBySign(sign);
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
    if (!employeeAuthorization.isAuthorized(employee, AccessLevel.READ, employeeDAO.getSupervisedEmployeeIds())) {
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

    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.DELETE)) {
      throw new RuntimeException("Illegal access to delete " + employeeId + " by " + authorizedUser.getLoginSign());
    }

    if(employee.isNew()) {
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
    
    // Delete the associated SalatUser if it exists
    if (employee.getSalatUser() != null) {
      salatUserRepository.delete(employee.getSalatUser());
    }
  }

  @Authorized(requiresManager = true)
  public void anonymizeEmployee(long employeeId, String confirmSign) {
    if (!authorizedUser.isManager()) throw new AuthorizationException(AA_NEEDS_MANAGER);
    var employee = employeeDAO.getEmployeeById(employeeId);
    if (!employee.getSign().equals(confirmSign)) {
      throw new InvalidDataException(EM_ANONYMIZE_WRONG_SIGN, employee.getSign());
    }
    var previousSign = employee.getSign();
    employee.setFirstname("Anonymized");
    employee.setLastname("User");
    employee.setSign(anonymousSign(employeeId));
    employee.setHide(true);
    eventPublisher.publishEvent(new EmployeeAnonymizedEvent(employeeId));
    if (employee.getSalatUser() != null) {
      employee.getSalatUser().setLoginname(anonymousLoginname(employeeId));
      salatUserRepository.save(employee.getSalatUser());
    }
    employeeRepository.save(employee);
    publishSignChange(employeeId, previousSign, employee.getSign());
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
   * For creating an employee and for changes that leave the sign alone. A change that touches the
   * sign belongs in {@link #createOrUpdate(Employee, String)} — records elsewhere reference the
   * employee by it and have to be told.
   */
  @Authorized(requiresManager = true)
  public void createOrUpdate(Employee employee) {
    createOrUpdate(employee, employee.getSign());
  }

  /**
   * Saves the employee and announces a changed sign to whoever references it, with {@code
   * previousSign} being the sign as it was stored before the change.
   *
   * <p>The caller has to pass it because by the time the employee arrives here it already carries
   * the new one: the form is applied to the loaded entity, so the old value is gone from the object
   * before the service ever sees it.
   */
  @Authorized(requiresManager = true)
  public void createOrUpdate(Employee employee, String previousSign) {
    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.WRITE)) {
      throw new RuntimeException("Illegal access to save " + employee.getId() + " by " + authorizedUser.getLoginSign());
    }

    // Ensure SalatUser is persisted before saving Employee
    if (employee.getSalatUser() != null) {
      SalatUser salatUser = employee.getSalatUser();
      salatUserRepository.save(salatUser);
    }

    employeeRepository.save(employee);
    publishSignChange(employee.getId(), previousSign, employee.getSign());
  }

  /**
   * Announces a changed sign, and only a changed one — a save that leaves the sign alone is the
   * normal case and must not make followers rewrite anything.
   */
  private void publishSignChange(long employeeId, String previousSign, String newSign) {
    if (previousSign == null || previousSign.equals(newSign)) {
      return;
    }
    eventPublisher.publishEvent(new EmployeeSignChangedEvent(employeeId, previousSign, newSign));
  }
}
