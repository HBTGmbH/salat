package org.tb.order.service;

import static org.tb.common.ErrorCode.SO_TIMEREPORT_EXISTS_OUTSIDE_VALIDITY;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.action.AddSuborderForm;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.SuborderVisitor;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

@Service
@Transactional
@RequiredArgsConstructor
public class SuborderService {

  private final SuborderDAO suborderDAO;
  private final TimereportDAO timereportDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final EmployeeorderService employeeorderService;
  private final TimereportService timereportService;
  private final CustomerorderDAO customerorderDAO;

  public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(long employeecontractId, long customerorderId, LocalDate date) {
    return suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontractId, customerorderId, date);
  }

  /**
   * Gets all {@link Timereport}s associated to the {@link Suborder} or his children, that are no longer valid for the given dates.
   */
  public List<TimereportDTO> getTimereportsNotMatchingNewSuborderOrderValidity(long suborderId, LocalDate newBegin, LocalDate newEnd) {

    final Suborder suborder = suborderDAO.getSuborderById(suborderId);

    /* build up result list */
    final List<TimereportDTO> allInvalidTimeReports = new LinkedList<>();

    /* create visitor to collect suborders */
    SuborderVisitor allInvalidTimeReportsCollector = so -> allInvalidTimeReports.addAll(
        timereportService.getTimereportsNotMatchingNewSuborderOrderValidity(
            so.getId(),
            newBegin,
            newEnd
        )
    );

    /* start visiting */
    suborder.acceptVisitor(allInvalidTimeReportsCollector);

    /* return result */
    return allInvalidTimeReports;
  }

  public Suborder createOrUpdate(Long soId, AddSuborderForm addSuborderForm, Customerorder customerorder) {
    Suborder so;
    if (soId != null) {
      // edited suborder
      so = suborderDAO.getSuborderById(soId);

      if (so.getSuborders() != null
          && !so.getSuborders().isEmpty()
          && !Objects.equals(so.getCustomerorder().getId(), customerorder.getId())) {
        // set customerorder in all descendants
        so.setCustomerOrderForAllDescendants(customerorder, suborderDAO, so);
      }
      so = suborderDAO.getSuborderById(soId);
    } else {
      // new report
      so = new Suborder();
    }
    so.setCustomerorder(customerorder);
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

    if (addSuborderForm.getValidFrom() != null && !addSuborderForm.getValidFrom().trim().equals("")) {
      LocalDate fromDate = DateUtils.parseOrNull(addSuborderForm.getValidFrom());
      so.setFromDate(fromDate);
    } else {
      so.setFromDate(so.getCustomerorder().getFromDate());
    }
    if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().equals("")) {
      LocalDate untilDate = DateUtils.parseOrNull(addSuborderForm.getValidUntil());
      so.setUntilDate(untilDate);
    } else {
      so.setUntilDate(null);
    }

    // adjust employeeorders
    if(!so.isNew()) {
      adjustEmployeeorderValidity(so);
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

    suborderDAO.save(so);

    return so;
  }

  public void adjustValidity(Suborder so, LocalDate fromDate, LocalDate untilDate) {
    boolean suborderchanged = false;
    if (so.getFromDate().isBefore(fromDate)) {
      so.setFromDate(fromDate);
      suborderchanged = true;
    }
    if (so.getUntilDate() != null && so.getUntilDate().isBefore(fromDate)) {
      so.setUntilDate(fromDate);
      suborderchanged = true;
    }
    if (untilDate != null) {
      if (so.getFromDate().isAfter(untilDate)) {
        so.setFromDate(untilDate);
        suborderchanged = true;
      }
      if (so.getUntilDate() == null || so.getUntilDate().isAfter(untilDate)) {
        so.setUntilDate(untilDate);
        suborderchanged = true;
      }
    }

    if (suborderchanged) {

      suborderDAO.save(so);

      adjustEmployeeorderValidity(so);
    }
  }

  private void adjustEmployeeorderValidity(Suborder so) {
    // adjust employeeorders
    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(so.getId());
    if (employeeorders != null && !employeeorders.isEmpty()) {
      for (Employeeorder employeeorder : employeeorders) {
        employeeorderService.adjustValidity(employeeorder, so.getFromDate(), so.getUntilDate());
      }
    }
  }

  public void fitValidityOfChildren(long suborderId) throws BusinessRuleException {
    var parent = suborderDAO.getSuborderById(suborderId);
    for (Suborder child : parent.getAllChildren()) {
      if(child.getFromDate().isBefore(parent.getFromDate())) {
        child.setFromDate(parent.getFromDate());
      }
      if(!parent.getOpenEnd() && child.getFromDate().isAfter(parent.getUntilDate())) {
        child.setFromDate(parent.getUntilDate());
      }
      if(child.getOpenEnd() && !parent.getOpenEnd()) {
        child.setUntilDate(parent.getUntilDate());
      }
      if(!child.getOpenEnd() && !parent.getOpenEnd() && child.getUntilDate().isAfter(parent.getUntilDate())) {
        child.setUntilDate(parent.getUntilDate());
      }
      if(!child.getOpenEnd() && child.getUntilDate().isBefore(child.getFromDate())) {
        child.setUntilDate(child.getFromDate());
      }
      validateBusinessRules(child);
      adjustEmployeeorderValidity(child);
      suborderDAO.save(child);

    }
  }

  private void validateBusinessRules(Suborder suborder) throws BusinessRuleException {
    if(timereportDAO.getTimereportsBySuborderIdInvalidForDates(
          suborder.getFromDate(),
          suborder.getUntilDate(),
          suborder.getId()
      ).stream().findAny().isPresent()) {
      throw new BusinessRuleException(SO_TIMEREPORT_EXISTS_OUTSIDE_VALIDITY);
    }
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
}
