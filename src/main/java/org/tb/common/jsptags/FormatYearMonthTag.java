package org.tb.common.jsptags;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import lombok.Setter;
import org.tb.common.util.DateUtils;

@Setter
public class FormatYearMonthTag extends TagSupport {

  private YearMonth value;
  private String pattern;

  @Override
  public int doStartTag() throws JspException {
    if(value != null) {
      JspWriter out = pageContext.getOut();
      try {
        if(pattern != null) {
          out.print(DateUtils.format(value, pattern));
        } else {
          out.print(DateUtils.format(value));
        }
        return super.doStartTag();
      } catch (IOException e) {
        throw new JspException(e);
      }
    }
    return SKIP_BODY;
  }

}