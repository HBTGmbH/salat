package org.tb.common.jsptags;

import java.io.IOException;
import java.time.LocalDate;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import lombok.Setter;
import org.tb.common.util.DateUtils;

@Setter
public class FormatLocalDateTag extends TagSupport {

  private LocalDate value;

  @Override
  public int doStartTag() throws JspException {
    if(value != null) {
      JspWriter out = pageContext.getOut();
      try {
        out.print(DateUtils.format(value));
        return super.doStartTag();
      } catch (IOException e) {
        throw new JspException(e);
      }
    }
    return SKIP_BODY;
  }

}
