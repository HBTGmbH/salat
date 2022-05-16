package org.tb.chicoree;

import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.parse;
import static org.tb.common.util.DateUtils.validateDate;

import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.tb.dailyreport.domain.TimereportDTO;

@Data
public class TimereportForm extends ActionForm {

  private String id;
  private String date;
  private String orderId;
  private String suborderId;
  private String hours;
  private String minutes;
  private String comment;

  public boolean isNew() {
    return id == null;
  }

  public long getIdTyped() {
    return Long.valueOf(id);
  }

  public LocalDate getDateTyped() {
    return parse(date);
  }

  public long getOrderIdTyped() {
    return Long.valueOf(orderId);
  }

  public long getSuborderIdTyped() {
    return Long.valueOf(suborderId);
  }

  public int getHoursTyped() {
    if(hours == null || hours.isBlank()) return 0;
    return Integer.valueOf(hours);
  }

  public int getMinutesTyped() {
    if(minutes == null || minutes.isBlank()) return 0;
    return Integer.valueOf(minutes);
  }

  @Override
  public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
    ActionErrors errors = new ActionErrors();
    if(date == null || date.isBlank()) {
      errors.add("date", new ActionMessage("form.timereport.error.date.missing"));
    }
    if(date != null && !date.isBlank() && !validateDate(date)) {
      errors.add("date", new ActionMessage("form.timereport.error.date.wrongformat"));
    }
    if(orderId == null || orderId.isBlank()) {
      errors.add("orderId", new ActionMessage("form.timereport.error.order.missing"));
    }
    if(orderId == null || orderId.isBlank()) {
      errors.add("suborderId", new ActionMessage("form.timereport.error.suborder.missing"));
    }
    if((hours == null || hours.isBlank()) && (minutes == null || minutes.isBlank())) {
      errors.add("hours", new ActionMessage("form.timereport.error.hours.unset"));
    }
    if(hours != null && !hours.isBlank()) {
      try {
        int hoursTyped = Integer.parseInt(hours);
        if(hoursTyped < 0 || hoursTyped > 23) {
          errors.add("hours", new ActionMessage("form.timereport.error.hours.wrongformat"));
        }
      } catch(NumberFormatException ignore) {
        errors.add("hours", new ActionMessage("form.timereport.error.hours.wrongformat"));
      }
    }
    if(minutes != null && !minutes.isBlank()) {
      try {
        Integer.parseInt(minutes);
        int minutesTyped = Integer.parseInt(minutes);
        if(minutesTyped < 0 || minutesTyped > 59) {
          errors.add("minutes", new ActionMessage("form.timereport.error.minutes.wrongformat"));
        }
      } catch(NumberFormatException ignore) {
        errors.add("minutes", new ActionMessage("form.timereport.error.minutes.wrongformat"));
      }
    }
    return errors;
  }

  public void init(TimereportDTO timereport) {
    id = String.valueOf(timereport.getId());
    date = format(timereport.getReferenceday());
    orderId = String.valueOf(timereport.getCustomerorderId());
    suborderId = String.valueOf(timereport.getSuborderId());
    hours = String.valueOf(timereport.getDuration().toHours());
    minutes = String.valueOf(timereport.getDuration().toMinutesPart());
    comment = timereport.getTaskdescription();
  }

  public void initNew(LocalDate date, ChicoreeSessionStore chicoreeSessionStore) {
    id = null;
    this.date = format(date);
    orderId = "";
    suborderId = "";
    hours = null;
    minutes = null;
    comment = null;
    chicoreeSessionStore.getLastStoredTimereport().ifPresent(lastTimereportForm -> {
      orderId = lastTimereportForm.getOrderId();
      suborderId = lastTimereportForm.getSuborderId();
    });
  }

}
