package org.tb.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.SalatProperties;
import org.tb.common.util.ClockProvider;
import org.tb.notification.persistence.NotificationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    private final NotificationRepository notificationRepository;
    private final SalatProperties salatProperties;

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void deleteExpiredNotifications() {
        int retentionDays = salatProperties.getNotifications().getRetentionDays();
        var cutoff = ClockProvider.now().minusDays(retentionDays);
        notificationRepository.deleteByCreatedBefore(cutoff);
        log.info("Deleted notifications older than {} days (before {})", retentionDays, cutoff);
    }

}
