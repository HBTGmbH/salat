package org.tb.notification.viewhelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.tb.notification.service.NotificationService;

@Slf4j
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class NotificationBellViewHelper {

    private final NotificationService notificationService;

    private Long cachedCount;

    public long getUnreadCount() {
        if (cachedCount == null) {
            try {
                cachedCount = notificationService.countUnreadForCurrentUser();
            } catch (Exception e) {
                log.debug("Could not fetch unread notification count", e);
                cachedCount = 0L;
            }
        }
        return cachedCount;
    }

}
