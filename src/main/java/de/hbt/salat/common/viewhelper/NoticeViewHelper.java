package de.hbt.salat.common.viewhelper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;

/**
 * Puts what a successful save could not settle by itself below the success message (#1206) — for
 * example a report definition that still names the old sign of a renamed order. The save went
 * through; each notice is something to look at by hand, not an error, so it travels with the
 * success toast as lines of its own.
 */
@Component
@RequiredArgsConstructor
public class NoticeViewHelper {

  static final String TOAST_SUCCESS_NOTICES = "toastSuccessNotices";

  private final ErrorCodeViewHelper errorCodeViewHelper;

  public void addNotices(RedirectAttributes redirectAttributes, List<ServiceFeedbackMessage> notices) {
    if (notices.isEmpty()) {
      return;
    }
    redirectAttributes.addFlashAttribute(TOAST_SUCCESS_NOTICES, notices.stream()
        .map(notice -> errorCodeViewHelper.toViewMessage(notice).resolved())
        .toList());
  }
}
