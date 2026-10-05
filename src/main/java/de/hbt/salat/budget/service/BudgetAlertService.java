package de.hbt.salat.budget.service;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.service.MailService;
import de.hbt.salat.common.service.MailService.MailContact;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.domain.CustomerorderResponsible;
import de.hbt.salat.order.service.CustomerorderService;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresManager = true)
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
                        sendAlert(budget.getId(), budget.getName(), budget.getCustomerorderId(),
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

    /**
     * The sign the plan's order has today, read by the plan's id (#1212).
     */
    private String customerorderSignOf(long customerorderId) {
        return customerorderService.getCustomerorderSignsByIds(List.of(customerorderId)).get(customerorderId);
    }

    /**
     * The order is read by the id of the plan, its responsibles come as values from the module
     * {@code order} — no entity of another module crosses into budget (#1340, ADR-0021). The text
     * still names the order by the sign it has today.
     */
    private void sendAlert(long budgetId, String budgetName, long customerorderId,
                           double utilization, int threshold, LocalDate today) {
        var coSign = customerorderSignOf(customerorderId);
        var responsibleEmployees = customerorderService.getResponsiblesByCustomerorderId(customerorderId);
        if (responsibleEmployees.isEmpty()) {
            log.warn("No responsible employees for customerorder {} — skipping alert for budget {}", coSign, budgetId);
            return;
        }

        var recipientUserIds = responsibleEmployees.stream()
            .map(CustomerorderResponsible::salatUserId)
            .filter(Objects::nonNull)
            .toList();

        // evaluate=true because the link is meant to show the evaluation, not just to preselect the
        // order — merely opening the page computes nothing (#1009). By id: the mail outlives a rename
        // of the order, the sign in its text is a snapshot (#1334).
        var controllingUrl = "/budget/controlling?fBudgetCustomerOrderId=" + customerorderId + "&evaluate=true";
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

        for (var responsible : responsibleEmployees) {
            var emailAddress = employeePreferenceService.getNotificationEmailForEmployeeId(responsible.employeeId());
            if (emailAddress == null || emailAddress.isBlank()) continue;
            try {
                var subject = MessageFormat.format(
                    messages.getMessage("main.budget.alert.email.subject"), budgetName);
                var body = MessageFormat.format(
                    messages.getMessage("main.budget.alert.email.body"),
                    budgetName, coSign, utilization, threshold, absoluteUrl);
                mailService.sendEmail(subject, body,
                    new MailContact("Salat Budget", alertEmailFrom),
                    new MailContact(responsible.name(), emailAddress));
            } catch (Exception e) {
                log.warn("Failed to send alert email to {}", responsible.name(), e);
            }
        }
    }
}
