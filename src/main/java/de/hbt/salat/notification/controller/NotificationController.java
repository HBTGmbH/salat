package de.hbt.salat.notification.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.notification.domain.Notification;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.notification.viewhelper.NotificationViewHelper;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Authorized
public class NotificationController {

    private final NotificationService notificationService;
    private final MessageSource messageSource;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String list(Model model) {
        List<NotificationViewHelper> notifications = toViewHelpers(notificationService.getAllForCurrentUser());
        model.addAttribute("notifications", notifications);
        model.addAttribute("pageTitle", "Benachrichtigungen");
        notificationService.markAllReadForCurrentUser();
        return "notification/list";
    }

    @GetMapping("/bell")
    public String bell(Model model) {
        List<NotificationViewHelper> notifications = toViewHelpers(notificationService.getLatestForCurrentUser());
        model.addAttribute("notifications", notifications);
        notificationService.markAllReadForCurrentUser();
        return "notification/bell-dropdown :: bellDropdown";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            notificationService.deleteNotification(id);
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream()
                            .map(Object::toString).findFirst().orElse("Fehler beim Löschen"));
        }
        return "redirect:/notifications";
    }

    @PostMapping("/clear-all")
    public String clearAll(RedirectAttributes redirectAttributes) {
        notificationService.deleteAllForCurrentUser();
        redirectAttributes.addFlashAttribute("toastSuccess", "Alle Benachrichtigungen gelöscht.");
        return "redirect:/notifications";
    }

    private List<NotificationViewHelper> toViewHelpers(List<Notification> notifications) {
        return notifications.stream()
                .map(n -> NotificationViewHelper.from(n, messageSource))
                .toList();
    }

}
