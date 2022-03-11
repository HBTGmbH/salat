package org.tb.chicoree;

import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.validateDate;

import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.tb.dailyreport.domain.Timereport;

@Data
public class TimereportForm extends ActionForm {

  private String id;
  private String date;
  private String orderId;
  private String suborderId;
  private String hours;
  private String minutes;
  private String comment;

  @Override
  public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
    ActionErrors errors = new ActionErrors();
    if(date == null || date.isBlank()) {
      errors.add("date", new ActionMessage("form.timereport.error.date.missing"));
    }
    if(date != null && !date.isBlank() && !validateDate(date)) {
      errors.add("date", new ActionMessage("form.timereport.error.date.wrongformat"));
    }
    if((hours == null || hours.isBlank()) && (minutes == null || minutes.isBlank())) {
      errors.add("hours", new ActionMessage("form.timereport.error.hours.unset"));
    }
    return errors;
  }

  public void init(Timereport timereport) {
    id = timereport.getId().toString();
    date = format(timereport.getReferenceday().getRefdate());
    orderId = timereport.getSuborder().getCustomerorder().getId().toString();
    suborderId = timereport.getSuborder().getId().toString();
    hours = timereport.getDurationhours().toString();
    minutes = timereport.getDurationminutes().toString();
    comment = timereport.getTaskdescription();
  }

  public void initNew(LocalDate date) {
    id = null;
    this.date = format(date);
    orderId = "";
    suborderId = "";
    hours = null;
    minutes = null;
    comment = null;
  }

}
