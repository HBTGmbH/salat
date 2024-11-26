package org.tb.common.jsptags;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.Setter;
import org.tb.common.util.DateUtils;

@Setter
public class FormatLocalDateTimeTag extends TagSupport {

  private LocalDateTime value;

  @Override
  public int doStartTag() throws JspException {
    if(value != null) {
      JspWriter out = pageContext.getOut();
      try {
        out.print(DateUtils.formatDateTime(value, "yyyy-MM-dd HH:mm:ss"));
        return super.doStartTag();
      } catch (IOException e) {
        throw new JspException(e);
      }
    }
    return SKIP_BODY;
  }

}
