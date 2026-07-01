package org.tb.notification.viewhelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.notification.persistence.NotificationRepository;

@Slf4j
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class NotificationBellViewHelper {

    private final NotificationRepository notificationRepository;
    private final AuthorizedEmployee authorizedEmployee;

    private Long cachedCount;

    public long getUnreadCount() {
        if (cachedCount == null) {
            try {
                cachedCount = notificationRepository.countByRecipientEmployeeIdAndReadFalse(
                        authorizedEmployee.getEmployeeId());
            } catch (Exception e) {
                log.debug("Could not fetch unread notification count", e);
                cachedCount = 0L;
            }
        }
        return cachedCount;
    }

}
