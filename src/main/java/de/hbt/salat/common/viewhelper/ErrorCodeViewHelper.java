package de.hbt.salat.common.viewhelper;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;

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
   *
   * <p>Ein Argument, das selbst eine Meldung ist, wird vorher aufgelöst: so setzt der CSV-Import die
   * Zeilennummer vor eine Meldung, die ohne Zeile formuliert ist, weil die REST-API sie ebenso
   * wirft (#1142).
   */
  public ViewMessage toViewMessage(ServiceFeedbackMessage message) {
    String key = toErrorKey(message.getErrorCode());
    Object[] args = message.getArguments().stream()
        .map(arg -> arg instanceof ServiceFeedbackMessage nested ? toViewMessage(nested).resolved() : arg)
        .toArray();
    String resolved = messages.getMessage(key, args, "???" + key + "???");
    return new ViewMessage(key, args, resolved);
  }

  /**
   * Die Meldung zu einem {@link ErrorCode} als Fehler-Toast der Umleitung — für eine Antwort, die nicht aus einer
   * Ausnahme kommt, etwa ein Objekt, das es zur id aus der Anfrage nicht gibt (#1401).
   */
  public void addToastError(RedirectAttributes redirectAttributes, ErrorCode errorCode) {
    redirectAttributes.addFlashAttribute("toastError", toViewMessage(ServiceFeedbackMessage.error(errorCode)).resolved());
  }

  public ViewMessage toViewMessage(String key) {
    var args = new Object[0];
    String resolved = messages.getMessage(key, args, "???" + key + "???");
    return new ViewMessage(key, args, resolved);
  }

  /**
   * Der Schlüssel, unter dem der Text eines {@link ErrorCode} in den Bündeln steht: {@code TR-0015}
   * → {@code errorcode.tr.0015}. Öffentlich, damit der Test, der jeden Code gegen beide Bündel
   * prüft, dieselbe Formel benutzt und nicht eine nachgebaute — eine Kopie driftet, und dann prüft
   * der Test einen Schlüssel, den niemand nachschlägt (#1083).
   */
  public static String toErrorKey(ErrorCode errorCode) {
    return errorCode.messageKey();
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
