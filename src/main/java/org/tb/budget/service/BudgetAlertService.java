package org.tb.budget.service;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.SalatProperties;
import org.tb.common.service.MailService;
import org.tb.common.service.MailService.MailContact;
import org.tb.common.util.DateUtils;
import org.tb.employee.preferences.EmployeePreferenceService;
import org.tb.notification.service.NotificationService;
import org.tb.order.service.CustomerorderService;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class BudgetAlertService {

    private final OrderBudgetRepository orderBudgetRepository;
    private final BudgetControllingService budgetControllingService;
    private final CustomerorderService customerorderService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final EmployeePreferenceService employeePreferenceService;
    private final OrderBudgetService orderBudgetService;
    private final MessageSourceAccessor messages;
    private final SalatProperties salatProperties;

    @Value("${salat.budget.alert.email.from:noreply@salat.local}")
    private String alertEmailFrom;

    public void checkAndNotify() {
        var today = DateUtils.today();
        var budgets = orderBudgetRepository.findByActiveAndAlertThresholdPercentIsNotNull(Boolean.TRUE);
        for (var budget : budgets) {
            try {
                var info = budgetControllingService.computeUtilizationInfo(budget);
                var utilization = info.percent();
                var threshold = budget.getAlertThresholdPercent();

                if (utilization >= threshold) {
                    if (budget.getAlertSentAt() == null) {
                        sendAlert(budget.getId(), budget.getName(), budget.getCustomerorderSign(),
                            utilization, threshold, today);
                        orderBudgetService.updateAlertSentAt(budget.getId(), today);
                        log.info("Budget alert sent for budget {} ({}): {}% >= {}%",
                            budget.getId(), budget.getName(), String.format("%.1f", utilization), threshold);
                    }
                } else {
                    if (budget.getAlertSentAt() != null) {
                        orderBudgetService.updateAlertSentAt(budget.getId(), null);
                        log.info("Budget alert reset for budget {} ({}): {}% < {}%",
                            budget.getId(), budget.getName(), String.format("%.1f", utilization), threshold);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to check alert for budget {} ({})", budget.getId(), budget.getName(), e);
            }
        }
    }

    private void sendAlert(long budgetId, String budgetName, String coSign,
                           double utilization, int threshold, LocalDate today) {
        var co = customerorderService.getCustomerorderBySign(coSign);
        var responsibleEmployees = co.getResponsibleHbt();
        if (responsibleEmployees == null || responsibleEmployees.isEmpty()) {
            log.warn("No responsible employees for customerorder {} — skipping alert for budget {}", coSign, budgetId);
            return;
        }

        var recipientUserIds = responsibleEmployees.stream()
            .filter(e -> e.getSalatUser() != null)
            .map(e -> e.getSalatUser().getId())
            .toList();

        var controllingUrl = "/budget/controlling?customerorderSign=" + coSign;
        var utilizationStr = String.format("%.1f", utilization);
        var thresholdStr = String.valueOf(threshold);

        notificationService.emitNotification(
            recipientUserIds,
            "main.budget.alert.notification.title",
            List.of(budgetName),
            "main.budget.alert.notification.description",
            List.of(budgetName, utilizationStr, thresholdStr),
            controllingUrl,
            messages.getMessage("main.budget.dashboard.link.controlling")
        );

        var baseUrl = salatProperties.getUrl() != null ? salatProperties.getUrl() : "";
        var absoluteUrl = baseUrl + controllingUrl;

        for (var employee : responsibleEmployees) {
            var emailAddress = employeePreferenceService.getNotificationEmailFor(employee);
            if (emailAddress == null || emailAddress.isBlank()) continue;
            try {
                var subject = MessageFormat.format(
                    messages.getMessage("main.budget.alert.email.subject"), budgetName);
                var body = MessageFormat.format(
                    messages.getMessage("main.budget.alert.email.body"),
                    budgetName, coSign, utilization, threshold, absoluteUrl);
                mailService.sendEmail(subject, body,
                    new MailContact("Salat Budget", alertEmailFrom),
                    new MailContact(employee.getName(), emailAddress));
            } catch (Exception e) {
                log.warn("Failed to send alert email to {}", employee.getName(), e);
            }
        }
    }
}
