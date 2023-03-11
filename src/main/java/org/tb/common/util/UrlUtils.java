package org.tb.common.util;

import javax.servlet.ServletContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UrlUtils {

  public static String absoluteUrl(String url, ServletContext context) {
    if(!url.startsWith("/")) {
      url = "/" + url;
    }
    if(context.getContextPath() != null && !context.getContextPath().isEmpty()) {
      url = context.getContextPath() + url;
    }
    return url;
  }

}
