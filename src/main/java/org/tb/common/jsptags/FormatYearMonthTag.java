package org.tb.common.jsptags;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.time.YearMonth;
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
