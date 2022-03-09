package org.tb.user;

import static org.tb.common.util.DateUtils.parseDateTime;

import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.tb.common.util.DateUtils;

@Getter
@Setter
public class UserAccessTokenForm extends ActionForm {

  public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
  private String validUntil;
  private String comment;

  @Override
  public void reset(ActionMapping mapping, HttpServletRequest request) {
    validUntil = DateUtils.formatDateTime(DateUtils.now().plusDays(1), DATE_TIME_FORMAT);
    comment = "";
  }

  @Override
  public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
    ActionErrors errors = new ActionErrors();
    if(!DateUtils.validateDateTime(validUntil,DATE_TIME_FORMAT)) {
      errors.add("validUntil", new ActionMessage("form.useraccesstoken.validation.validuntil.format"));
    }
    return errors;
  }

  public LocalDateTime getParsedValidUntil() {
    return parseDateTime(validUntil, DATE_TIME_FORMAT);
  }

}
