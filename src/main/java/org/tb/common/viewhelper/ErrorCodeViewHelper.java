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
    return ex.getMessages().stream().map(this::toViewMessage).collect(Collectors.toList());
  }

  /**
   * Die einzelne Meldung, aufgelöst. Nicht jede Meldung kommt aus einer Ausnahme: eine Bedingung,
   * die festgehalten und später beantwortet wird, trägt dieselbe {@link ServiceFeedbackMessage}
   * ohne je geworfen worden zu sein (#1054).
   */
  public ViewMessage toViewMessage(ServiceFeedbackMessage message) {
    String key = toErrorKey(message);
    Object[] args = message.getArguments().toArray();
    String resolved = messages.getMessage(key, args, "???" + key + "???");
    return new ViewMessage(key, args, resolved);
  }

  public ViewMessage toViewMessage(String key) {
    var args = new Object[0];
    String resolved = messages.getMessage(key, args, "???" + key + "???");
    return new ViewMessage(key, args, resolved);
  }

  private String toErrorKey(ServiceFeedbackMessage m) {
    // TR-0015 -> errorcode.tr.0015
    return "errorcode." + m.getErrorCode().getCode().replace('-', '.').toLowerCase();
  }

  public record ViewMessage(String key, Object[] args, String resolved) {

    /**
     * Controllers build the text of a toast notification via {@code map(Object::toString)}. What the
     * user has to read there is the resolved message - the record's default rendering would leak
     * the message key and an array identity into the UI (#825).
     */
    @Override
    public String toString() {
      return resolved;
    }
  }

}
