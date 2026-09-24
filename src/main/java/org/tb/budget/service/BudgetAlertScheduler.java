package org.tb.budget.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.scheduling.SchedulerRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAlertScheduler {

    private final BudgetAlertService budgetAlertService;
    private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

    @Scheduled(cron = "${salat.budget.alert.cron:0 0 6 * * *}")
    public void run() {
        setRequestAttributes(new SchedulerRequestAttributes(), true);
        try {
            AuthorizedUser systemUser = authorizedUserProvider.getObject();
            systemUser.initForJob();
            budgetAlertService.checkAndNotify();
        } catch (Exception e) {
            log.error("Budget alert scheduler failed", e);
        } finally {
            // No bean is destroyed by hand: resetRequestAttributes() drops the whole scope with the
            // bean inside it. Why destroyScopedBean("authorizedUser") must not come back here is
            // written down on SchedulerRequestAttributes (#1084).
            resetRequestAttributes();
        }
    }
}
