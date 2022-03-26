package org.tb.order.service;

import static org.tb.common.ErrorCode.SO_TIMEREPORT_EXISTS_OUTSIDE_VALIDITY;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.exception.BusinessRuleException;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;

@Service
@RequiredArgsConstructor
public class SuborderService {

  private final SuborderDAO suborderDAO;
  private final TimereportDAO timereportDAO;

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

}
