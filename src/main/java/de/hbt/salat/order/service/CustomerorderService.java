package de.hbt.salat.order.service;

import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;
import static de.hbt.salat.order.command.GetTimereportMinutesCommandEvent.OrderType.CUSTOMER;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.customer.event.CustomerDeleteEvent;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.command.GetTimereportMinutesCommandEvent;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.CustomerorderResponsible;
import de.hbt.salat.order.domain.CustomerorderSearchRow;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.domain.ResponsibleOption;
import de.hbt.salat.order.event.CustomerorderDeleteEvent;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class CustomerorderService {

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final CustomerorderDAO customerorderDAO;
  private final CustomerDAO customerDAO;
  private final EmployeeDAO employeeDAO;
  private final CustomerorderRepository customerorderRepository;
  private final SpecialOrders specialOrders;

  /**
   * The orders the command palette considers for a query (#1157), hidden and ended ones last. There
   * is no rule per order: whoever is not restricted sees every order on the list pages, so no filter
   * applies here beyond the query — the palette's provider keeps restricted users out.
   */
  @Transactional(readOnly = true)
  public List<CustomerorderSearchRow> getPaletteCandidates(PaletteQuery query) {
    return customerorderRepository.findPaletteCandidates(query.likeWord(0), query.likeWord(1),
        query.likeWord(2), DateUtils.today(), PageRequest.of(0, PaletteQuery.CANDIDATE_LIMIT));
  }

  public List<Customerorder> getCustomerordersWithValidEmployeeOrders(long employeeContractId, final LocalDate date) {
    return customerorderDAO.getCustomerordersWithValidEmployeeOrders(employeeContractId, date);
  }

  @Authorized(requiresManager = true)
  public Customerorder create(CustomerorderDTO dto) {
    return createOrUpdate(null, dto, new ArrayList<>());
  }

  /**
   * @return what could not follow a renamed order by itself (#1206) and needs a look by hand —
   *     usually nothing
   */
  @Authorized(requiresManager = true)
  public List<ServiceFeedbackMessage> update(long customerorderId, CustomerorderDTO dto) {
    var notices = new ArrayList<ServiceFeedbackMessage>();
    createOrUpdate(customerorderId, dto, notices);
    return notices;
  }

  private Customerorder createOrUpdate(Long coId, CustomerorderDTO dto, List<ServiceFeedbackMessage> notices) {

    Customerorder co;
    if (coId != null) {
      co = customerorderDAO.getCustomerorderById(coId);
    } else {
      // new customer order
      co = new Customerorder();
    }
    // the sign before the edit — signs stay changeable, and what names the order by sign follows (#1206)
    var oldSign = co.isNew() ? null : co.getSign();
    // except for a special order: the configuration names it by sign (#1341, ADR-0035)
    var signChanges = oldSign != null && !oldSign.equals(dto.sign());
    if (signChanges && specialOrders.isLockedCustomerorder(co.getId())) {
      throw new BusinessRuleException(ErrorCode.CO_SPECIAL_ORDER_LOCKED, oldSign);
    }

    /* set attributes */
    co.setCustomer(customerDAO.getCustomerById(dto.customerId()));

    co.setUntilDate(dto.untilDate());
    co.setFromDate(dto.fromDate());

    co.setSign(dto.sign());
    co.setDescription(dto.description());
    co.setShortdescription(dto.shortdescription());
    co.setOrder_customer(dto.orderCustomer());

    co.setResponsible_customer_contractually(dto.responsibleCustomerContractually());
    co.setResponsible_customer_technical(dto.responsibleCustomerTechnical());

    if (dto.responsibleHbtIds() == null || dto.responsibleHbtIds().isEmpty()) {
      throw new InvalidDataException(ErrorCode.CO_RESPONSIBLE_HBT_REQUIRED);
    }
    if (dto.respEmpHbtContractId() == null) {
      throw new InvalidDataException(ErrorCode.CO_RESP_CONTRACT_EMPLOYEE_REQUIRED);
    }
    // each person once - the table refuses a second row for the same pair (#1208)
    co.setResponsibleHbt(dto.responsibleHbtIds().stream().distinct().map(employeeDAO::getEmployeeById).toList());
    co.setRespEmpHbtContract(employeeDAO.getEmployeeById(dto.respEmpHbtContractId()));

    if (dto.debithours() == null
        || dto.debithours().isEmpty()
        || DurationUtils.parseDuration(dto.debithours()).isZero()) {
      co.setDebithours(Duration.ZERO);
      co.setDebithoursunit(null);
    } else {
      co.setDebithours(DurationUtils.parseDuration(dto.debithours()));
      co.setDebithoursunit(dto.debithoursunit());
    }

    co.setHide(dto.hide());

    co.setOrderType(dto.orderType());

    // an order always has a setting (#1326); "at most" needs a number of at least 1
    var ticketReferencePolicy = TicketReferencePolicy.of(dto.ticketReferenceMode(), dto.ticketReferenceLimit());
    if (ticketReferencePolicy == null) {
      throw new InvalidDataException(ErrorCode.CO_TICKET_REFERENCE_LIMIT_INVALID);
    }
    co.setTicketReferencePolicy(ticketReferencePolicy);

    if(!co.isNew()) {
      var event = new CustomerorderUpdateEvent(co);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            ErrorCode.CO_UPDATE_GOT_VETO,
            co.getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }
    var saved = customerorderRepository.save(co);
    if (oldSign != null && !oldSign.equals(saved.getSign())) {
      var renamed = new SignsRenamedEvent(oldSign, saved.getSign(), saved.getId(), saved.getId());
      eventPublisher.publishEvent(renamed);
      notices.addAll(renamed.getNotices());
    }
    return saved;
  }

  public Customerorder getCustomerorderBySign(String selectedOrder) {
    return customerorderDAO.getCustomerorderBySign(selectedOrder);
  }

  /**
   * The orders behind a set of signs, hidden and expired ones included — for labelling records that
   * reference their order by sign and outlive it (#949).
   */
  @Transactional(readOnly = true)
  public List<Customerorder> getCustomerordersBySigns(Collection<String> signs) {
    return signs.isEmpty() ? List.of() : customerorderRepository.findBySignIn(signs);
  }

  public List<Customerorder> getCustomerordersByEmployeeContractId(long employeeContractId) {
    return customerorderDAO.getCustomerordersByEmployeeContractId(employeeContractId);
  }

  /**
   * The id of the order with this sign, or {@code null} — for a module that keeps the id and gets a
   * sign from the user, e.g. as a filter value (#1212).
   */
  @Transactional(readOnly = true)
  public Long getCustomerorderIdBySign(String sign) {
    return sign == null ? null : customerorderRepository.findIdBySign(sign).orElse(null);
  }

  /**
   * The orders with these ids as a select offers them, ordered by sign, hidden and expired ones
   * included — what another module needs to name orders it refers to by id (#1212, ADR-0021).
   */
  @Transactional(readOnly = true)
  public List<CustomerorderOption> getCustomerorderOptionsByIds(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : customerorderRepository.findOptionsByIdIn(ids);
  }

  /** The signs of the orders with these ids, by id — an id without an order is missing (#1212). */
  @Transactional(readOnly = true)
  public Map<Long, String> getCustomerorderSignsByIds(Collection<Long> ids) {
    return getCustomerorderOptionsByIds(ids).stream()
        .collect(Collectors.toMap(CustomerorderOption::id, CustomerorderOption::sign));
  }

  /**
   * The ids of the orders with these signs, by sign — a sign without an order is missing. For a
   * module that is handed signs and links by id, e.g. the targets of the command palette (#1334).
   */
  @Transactional(readOnly = true)
  public Map<String, Long> getCustomerorderIdsBySigns(Collection<String> signs) {
    return signs.isEmpty() ? Map.of() : customerorderRepository.findOptionsBySignIn(signs).stream()
        .collect(Collectors.toMap(CustomerorderOption::sign, CustomerorderOption::id));
  }

  /** The orders with these ids, in one statement — for a caller that resolved the ids elsewhere (#1092). */
  public List<Customerorder> getCustomerordersByIds(Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return Lists.newArrayList(customerorderRepository.findAllById(ids));
  }

  public List<Customerorder> getAllCustomerorders() {
    return customerorderDAO.getCustomerorders();
  }

  /**
   * Customer orders offered in a select box: everything not hidden, plus the one carrying
   * {@code keepSign} even if it is hidden. Orders are routinely hidden once they are finished, and a
   * record already referencing such an order has to stay editable.
   */
  public List<Customerorder> getSelectableCustomerorders(String keepSign) {
    return getAllCustomerorders().stream()
        .filter(customerorder -> !customerorder.getHide()
            || Objects.equals(customerorder.getSign(), keepSign))
        .toList();
  }

  /**
   * {@link #getSelectableCustomerorders} as options, for a module that refers to the order by id
   * (#1343, ADR-0021): everything not hidden, plus the order {@code keepId} even if it is hidden.
   */
  @Transactional(readOnly = true)
  public List<CustomerorderOption> getSelectableCustomerorderOptions(Long keepId) {
    return customerorderRepository.findSelectableOptions(keepId);
  }

  /**
   * The orders a select for something new offers, as options (#1386, ADR-0029): neither hidden nor
   * inactive today, plus the order {@code keepId} whatever it is.
   */
  @Transactional(readOnly = true)
  public List<CustomerorderOption> getCreatableCustomerorderOptions(Long keepId) {
    return customerorderRepository.findCreatableOptions(keepId, DateUtils.today());
  }

  /**
   * The orders the login is responsible for, as options, hidden ones left out (#1386) — for a
   * module that grants its own rights over the responsibility and refers to the order by id.
   */
  @Transactional(readOnly = true)
  public List<CustomerorderOption> getResponsibleCustomerorderOptions(long salatUserId) {
    return customerorderRepository.findResponsibleOptionsBySalatUserId(salatUserId);
  }

  public List<CustomerorderOption> getInvoiceableCustomerorders() {
    return customerorderDAO.getInvoiceableCustomerorders();
  }

  public Customerorder getCustomerorderById(long customerorderId) {
    return customerorderDAO.getCustomerorderById(customerorderId);
  }

  @Authorized(requiresManager = true)
  public Customerorder toggleHide(long id) {
    Customerorder co = customerorderDAO.getCustomerorderById(id);
    if (co == null) throw new InvalidDataException(ErrorCode.CO_NOT_FOUND);
    co.setHide(!co.getHide());
    return customerorderRepository.save(co);
  }

  public List<Customerorder> getCustomerOrdersByResponsibleEmployeeId(Long responsibleEmployeeId) {
    return customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(responsibleEmployeeId);
  }

  /**
   * The responsibles of the order ({@code responsibleHbt}) as values, empty when there is no order
   * with this id — for a module that notifies them and must not hold the employees (#1340,
   * ADR-0021).
   */
  @Transactional(readOnly = true)
  public List<CustomerorderResponsible> getResponsiblesByCustomerorderId(long customerorderId) {
    var customerorder = customerorderDAO.getCustomerorderById(customerorderId);
    if (customerorder == null || customerorder.getResponsibleHbt() == null) {
      return List.of();
    }
    return customerorder.getResponsibleHbt().stream()
        .map(CustomerorderResponsible::of)
        .toList();
  }

  /**
   * The ids of every customer order belonging to a customer of this segment. Ids rather than orders
   * because the callers use them to restrict a query, not to display the orders (#1340).
   */
  @Transactional(readOnly = true)
  public List<Long> getIdsByCustomerSegmentId(long segmentId) {
    return customerorderRepository.findIdsByCustomerSegmentId(segmentId);
  }

  /**
   * The ids of every customer order that lists this employee among its responsibles
   * ({@code responsibleHbt}) — the role the "responsible" filters offer
   * ({@link #getVisibleResponsibleEmployees}). Unlike {@link #getIdsByResponsibleEmployeeId}, the
   * responsible of the contract ({@code respEmpHbtContract}) does not count (#1340).
   */
  @Transactional(readOnly = true)
  public List<Long> getIdsByResponsibleHbtEmployeeId(long responsibleEmployeeId) {
    return customerorderRepository.findIdsByResponsibleHbt(responsibleEmployeeId);
  }

  /**
   * The ids of every order this employee is responsible for, in either role — {@code responsibleHbt} or
   * {@code respEmpHbtContract} (#1092). Ids rather than orders: the caller turns them into a condition of its own
   * query and never displays them.
   */
  public List<Long> getIdsByResponsibleEmployeeId(long responsibleEmployeeId) {
    return customerorderRepository.findIdsByResponsibleEmployee(responsibleEmployeeId);
  }

  /**
   * Every employee who is responsible for at least one customer order, ordered by sign — offered in
   * select boxes, so hidden orders and hidden employees are left out. A responsibility on a hidden
   * order has expired with it, which is also how budget access is decided.
   */
  /**
   * The employees offered by a "responsible" filter: everyone responsible for at least one visible
   * order, narrowed to one customer segment when {@code customerSegmentId} is given (#952).
   */
  public List<ResponsibleOption> getVisibleResponsibleEmployees(Long customerSegmentId) {
    return customerSegmentId == null
        ? customerorderRepository.findAllVisibleResponsibleHbt()
        : customerorderRepository.findVisibleResponsibleHbtByCustomerSegmentId(customerSegmentId);
  }

  public List<Customerorder> getVisibleCustomerorders() {
    return customerorderDAO.getVisibleCustomerorders();
  }

  /**
   * The orders a filter over existing bookings may offer: not hidden, inactive ones included
   * (#1106). A list of what has already happened has to name the orders it happened on, and those
   * expire while their bookings stay.
   *
   * <p>For a select box that picks something new this is the wrong list — that one is
   * {@link #getVisibleCustomerorders()}.
   */
  public List<Customerorder> getNotHiddenCustomerorders() {
    return customerorderDAO.getNotHiddenCustomerorders();
  }

  @Authorized(requiresManager = true)
  public void deleteCustomerorderById(long customerOrderId) {
    var event = new CustomerorderDeleteEvent(customerOrderId);
    var customerorder = customerorderDAO.getCustomerorderById(customerOrderId);
    if (customerorder == null) throw new InvalidDataException(ErrorCode.CO_NOT_FOUND);
    if (specialOrders.isLockedCustomerorder(customerOrderId)) {
      throw new BusinessRuleException(ErrorCode.CO_SPECIAL_ORDER_LOCKED, customerorder.getSign());
    }
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(
          ErrorCode.CO_DELETE_GOT_VETO,
          customerorder.getSign()
      ));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    customerorderRepository.deleteById(customerOrderId);
  }

  public List<Customerorder> getCustomerordersByFilters(Boolean showInactive, String filter, Long customerId, Boolean showHidden) {
    return customerorderDAO.getCustomerordersByFilters(showInactive, filter, customerId, showHidden);
  }

  @EventListener
  void onCustomerDelete(CustomerDeleteEvent event) {
    var customerorders = customerorderRepository.findAllByCustomerId(event.getId());
    for (Customerorder customerorder : customerorders) {
      deleteCustomerorderById(customerorder.getId());
    }
  }

  public Duration getTotalDuration(long customerorderId) {
    var command = GetTimereportMinutesCommandEvent.builder()
        .orderType(CUSTOMER)
        .orderIds(List.of(customerorderId))
        .build();
    commandPublisher.publish(command);
    return command.getResult().getOrDefault(customerorderId, Duration.ZERO);
  }

}
