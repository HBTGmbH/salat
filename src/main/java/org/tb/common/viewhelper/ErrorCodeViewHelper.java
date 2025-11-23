package org.tb.common.viewhelper;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.ServiceFeedbackMessage;

@Component
@AllArgsConstructor
// maybe someday a better name
public class ErrorCodeViewHelper {

  private final MessageSourceAccessor messages;

  public List<ViewMessage> toViewMessages(ErrorCodeException ex) {
    return ex.getMessages().stream().map(m -> {
      String key = toErrorKey(m);
      Object[] args = m.getArguments().toArray();
      String resolved = messages.getMessage(key, args, "???" + key + "???");
      return new ViewMessage(key, args, resolved);
    }).collect(Collectors.toList());
  }

  private String toErrorKey(ServiceFeedbackMessage m) {
    // TR-0015 -> errorcode.tr.0015
    return "errorcode." + m.getErrorCode().getCode().replace('-', '.').toLowerCase();
  }

  public record ViewMessage(String key, Object[] args, String resolved) {}

}
