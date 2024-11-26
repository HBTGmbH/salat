package org.tb.common.jsptags;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.time.Duration;
import lombok.Setter;
import org.tb.common.util.DurationUtils;

@Setter
public class FormatDurationTag extends TagSupport {

  private Duration value;

  @Override
  public int doStartTag() throws JspException {
    if(value != null) {
      JspWriter out = pageContext.getOut();
      try {
        out.print(DurationUtils.format(value));
        return super.doStartTag();
      } catch (IOException e) {
        throw new JspException(e);
      }
    }
    return SKIP_BODY;
  }

}
