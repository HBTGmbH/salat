package org.tb.budget.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.reporting.service.ScheduledReportJobScheduler.SchedulerMockRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAlertScheduler {

    private final BudgetAlertService budgetAlertService;
    private final ConfigurableListableBeanFactory beanFactory;
    private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

    @Scheduled(cron = "${salat.budget.alert.cron:0 0 6 * * *}")
    public void run() {
        setRequestAttributes(new SchedulerMockRequestAttributes(), true);
        try {
            AuthorizedUser systemUser = authorizedUserProvider.getObject();
            systemUser.initForJob();
            budgetAlertService.checkAndNotify();
        } catch (Exception e) {
            log.error("Budget alert scheduler failed", e);
        } finally {
            try { beanFactory.destroyScopedBean("authorizedUser"); } catch (Exception ignored) {}
            resetRequestAttributes();
        }
    }
}
