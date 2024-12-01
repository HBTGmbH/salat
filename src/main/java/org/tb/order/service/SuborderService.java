package org.tb.order.service;

import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.order.command.GetTimereportMinutesCommandEvent.OrderType.SUB;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.command.CommandPublisher;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.order.action.AddSuborderForm;
import org.tb.order.command.GetTimereportMinutesCommandEvent;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.event.CustomerorderDeleteEvent;
import org.tb.order.event.CustomerorderUpdateEvent;
import org.tb.order.event.SuborderDeleteEvent;
import org.tb.order.event.SuborderUpdateEvent;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.persistence.SuborderRepository;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class SuborderService {

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final SuborderDAO suborderDAO;
  private final SuborderRepository suborderRepository;

  public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(long employeecontractId, long customerorderId, LocalDate date) {
    return suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontractId, customerorderId, date);
  }

  @Authorized(requiresManager = true)
  public void create(AddSuborderForm addSuborderForm, Customerorder customerorder) {
    createOrUpdate(null, addSuborderForm, customerorder);
  }

  @Authorized(requiresManager = true)
  public void update(long suborderId, AddSuborderForm addSuborderForm, Customerorder customerorder) {
    createOrUpdate(suborderId, addSuborderForm, customerorder);
  }

  private void createOrUpdate(Long soId, AddSuborderForm addSuborderForm, Customerorder customerorder) {
    Suborder so;
    if (soId != null) {
      // edited suborder
      so = suborderDAO.getSuborderById(soId);
    } else {
      // new report
      so = new Suborder();
    }
    so.acceptVisitor(suborder -> suborder.setCustomerorder(customerorder));
    so.setSign(addSuborderForm.getSign());
    so.setSuborder_customer(addSuborderForm.getSuborder_customer());
    so.setDescription(addSuborderForm.getDescription());
    so.setShortdescription(addSuborderForm.getShortdescription());
    so.setInvoice(addSuborderForm.getInvoice());
    so.setStandard(addSuborderForm.getStandard());
    so.setCommentnecessary(addSuborderForm.getCommentnecessary());
    so.setFixedPrice(addSuborderForm.getFixedPrice());
    so.setTrainingFlag(addSuborderForm.getTrainingFlag());
    so.setOrderType(addSuborderForm.getOrderType());

    if (addSuborderForm.getValidFrom() != null && !addSuborderForm.getValidFrom().trim().isEmpty()) {
      LocalDate fromDate = DateUtils.parseOrNull(addSuborderForm.getValidFrom());
      so.setFromDate(fromDate);
    } else {
      so.setFromDate(so.getCustomerorder().getFromDate());
    }
    if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().isEmpty()) {
      LocalDate untilDate = DateUtils.parseOrNull(addSuborderForm.getValidUntil());
      so.setUntilDate(untilDate);
    } else {
      so.setUntilDate(null);
    }

    if (addSuborderForm.getDebithours() == null
        || addSuborderForm.getDebithours().isEmpty()
        || DurationUtils.parseDuration(addSuborderForm.getDebithours()).isZero()) {
      so.setDebithours(Duration.ZERO);
      so.setDebithoursunit(null);
    } else {
      so.setDebithours(DurationUtils.parseDuration(addSuborderForm.getDebithours()));
      so.setDebithoursunit(addSuborderForm.getDebithoursunit());
    }

    so.setHide(addSuborderForm.getHide());
    Suborder parentOrderCandidate = suborderDAO.getSuborderById(addSuborderForm.getParentId());
    // Falls die Suborder nicht zum Customerorder passt (Kollision der IDs), ist sie kein geeigneter Kandidat (HACK, da UI die ID manchmal auch mit CustomerOrderID besetzt)
    if (parentOrderCandidate != null && parentOrderCandidate.getCustomerorder().getId() != addSuborderForm.getCustomerorderId()) {
      if (!addSuborderForm.getParentId().equals(addSuborderForm.getCustomerorderId())) {
        throw new IllegalStateException("parentId is neither a valid suborderId nor the customerorderId, but: " + addSuborderForm.getParentId());
      }
      parentOrderCandidate = null;
    }
    so.setParentorder(parentOrderCandidate);

    if(!so.isNew()) {
      var event = new SuborderUpdateEvent(so);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(ErrorCode.SO_UPDATE_GOT_VETO, so.getCompleteOrderSign()));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }

    suborderRepository.save(so);
  }

  @EventListener
  void onCustomerorderUpdate(CustomerorderUpdateEvent event) {
    var customerorder = event.getDomainObject();
    var newValidity = customerorder.getValidity();

    // adjust suborders
    List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(customerorder.getId(), false);
    for (Suborder suborder : suborders) {
      var existingValidity = suborder.getValidity();
      var updating = existingValidity.overlaps(newValidity);
      if(updating) {
        adjustValidity(suborder.getId(), newValidity);
      } else {
        deleteSuborderById(suborder.getId());
      }
    }
  }

  @EventListener
  void onCustomerorderDelete(CustomerorderDeleteEvent event) {
    var suborders = suborderDAO.getSubordersByCustomerorderId(event.getId(), false);
    for (Suborder suborder : suborders) {
      deleteSuborderById(suborder.getId());
    }
  }

  public Duration getTotalDuration(List<Long> suborderIds) {
    var command = GetTimereportMinutesCommandEvent.builder()
        .orderType(SUB)
        .orderIds(suborderIds)
        .build();
    commandPublisher.publish(command);
    return command.getResult().values().stream().reduce(Duration::plus).orElse(Duration.ZERO);
  }

  private void adjustValidity(long suborderId, LocalDateRange newValidity) {
    var suborder = suborderDAO.getSuborderById(suborderId);
    var existingValidity = suborder.getValidity();
    var resultingValidity = existingValidity.intersection(newValidity);
    var newFrom = resultingValidity.getFrom();
    var newUntil = resultingValidity.getUntil();
    AddSuborderForm soForm = createForm(suborder, newFrom, newUntil);
    createOrUpdate(suborderId, soForm, suborder.getCustomerorder());
  }

  @Deprecated
  private AddSuborderForm createForm(Suborder so, LocalDate newFrom, LocalDate newUntil) {
    AddSuborderForm soForm = new AddSuborderForm();
    soForm.setCustomerorderId(so.getCustomerorder().getId());
    soForm.setSign(so.getCompleteOrderSign());
    soForm.setDescription(so.getDescription());
    soForm.setShortdescription(so.getShortdescription());
    soForm.setInvoice(so.getInvoice());
    soForm.setStandard(so.getStandard());
    soForm.setCommentnecessary(so.getCommentnecessary());
    soForm.setTrainingFlag(so.getTrainingFlag());
    soForm.setFixedPrice(so.getFixedPrice());
    soForm.setSuborder_customer(so.getSuborder_customer());
    if (so.getParentorder() != null) {
      soForm.setParentId(so.getParentorder().getId());
    } else {
      soForm.setParentId(so.getCustomerorder().getId());
    }
    soForm.setValidFrom(DateUtils.format(newFrom));
    if (newUntil != null) {
      soForm.setValidUntil(DateUtils.format(newUntil));
    } else {
      soForm.setValidUntil("");
    }

    if (so.getDebithours() != null && !so.getDebithours().isZero()) {
      soForm.setDebithours(DurationUtils.format(so.getDebithours()));
      soForm.setDebithoursunit(so.getDebithoursunit());
    } else {
      soForm.setDebithours(null);
      soForm.setDebithoursunit(null);
    }
    soForm.setHide(so.isHide());
    soForm.setOrderType(so.getOrderType());

    return soForm;
  }

  @Authorized(requiresManager = true)
  public void fitValidityOfChildren(long suborderId) throws BusinessRuleException {
    var parent = suborderDAO.getSuborderById(suborderId);
    for (Suborder child : parent.getAllChildren()) {
      adjustValidity(child.getId(), new LocalDateRange(parent.getFromDate(), parent.getUntilDate()));
    }
  }

  public List<Suborder> getSubordersByCustomerorderId(long customerorderId) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId, false);
  }

  public List<Suborder> getSubordersByCustomerorderId(long customerorderId, boolean showOnlyValid) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId, showOnlyValid);
  }

  public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderId(Long employeeContractId, long customerorderId,
      boolean showOnlyValid) {
    return suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(employeeContractId, customerorderId, showOnlyValid);
  }

  public Suborder getSuborderById(long suborderId) {
    return suborderDAO.getSuborderById(suborderId);
  }

  public List<Suborder> getSubordersByEmployeeContractId(long employeeContractId) {
    return suborderDAO.getSubordersByEmployeeContractId(employeeContractId);
  }

  public List<Suborder> getAllSuborders() {
    return suborderDAO.getSuborders(false);
  }

  @Authorized(requiresManager = true)
  public void deleteSuborderById(long suborderId) {
    var event = new SuborderDeleteEvent(suborderId);
    var suborder = suborderDAO.getSuborderById(suborderId);
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(ErrorCode.SO_DELETE_GOT_VETO, suborder.getCompleteOrderSign()));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    suborderRepository.deleteById(suborderId);
  }

  public List<Suborder> getSubordersByFilters(Boolean showInvalid, String filter, Long customerOrderId) {
    return suborderDAO.getSubordersByFilters(showInvalid, filter, customerOrderId);
  }

  public List<Suborder> getSubordersByValidity(Boolean showOnlyValid) {
    return suborderDAO.getSuborders(showOnlyValid);
  }

  public List<Suborder> getSuborderChildren(Long parentSuborderId) {
    return suborderDAO.getSuborderChildren(parentSuborderId);
  }

  public List<Suborder> getSubordersByEmployeeContractIdWithValidEmployeeOrders(long employeecontractId,
      LocalDate validAt) {
    return suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(employeecontractId, validAt);
  }

  @Authorized(requiresManager = true)
  public void createCopy(long suborederId) {
    var suborder = getSuborderById(suborederId);
    var copy = createCopy(suborder, true);
    suborderRepository.save(copy);
  }

  @Authorized(requiresManager = true)
  public void changeSuborder_customer(long suborderId, String suborder_customer) {
    getSuborderById(suborderId).setSuborder_customer(suborder_customer);
  }

  @Authorized(requiresManager = true)
  public void hideSuborders(List<Long> suborderIds) {
    for (long suborderId : suborderIds) {
      getSuborderById(suborderId).setHide(true);
    }
  }

  private Suborder createCopy(Suborder suborder, boolean copyroot) {
    Suborder copy = new Suborder();

    // set attrib values in copy
    copy.setCommentnecessary(suborder.getCommentnecessary());
    copy.setCustomerorder(suborder.getCustomerorder());
    copy.setDebithours(suborder.getDebithours()); // see #getDebithours
    copy.setDebithoursunit(suborder.getDebithoursunit());
    copy.setDescription(suborder.getDescription());
    copy.setFromDate(suborder.getFromDate());
    copy.setHide(suborder.isHide());
    copy.setInvoice(suborder.getInvoice());
    copy.setShortdescription(suborder.getShortdescription());
    copy.setStandard(suborder.getStandard());
    copy.setUntilDate(suborder.getUntilDate());
    copy.setSign(suborder.getSign());
    copy.setSuborder_customer(suborder.getSuborder_customer());
    copy.setFixedPrice(suborder.getFixedPrice());
    copy.setTrainingFlag(suborder.getTrainingFlag());
    copy.setOrderType(suborder.getOrderType());

    if (copyroot) {
      copy.setSign("copy_of_" + suborder.getSign());
    }

    for (Suborder child : suborder.getSuborders()) {
      Suborder childCopy = createCopy(child, false);
      childCopy.setParentorder(copy);
      copy.addSuborder(childCopy);
    }
    return copy;
  }
}
