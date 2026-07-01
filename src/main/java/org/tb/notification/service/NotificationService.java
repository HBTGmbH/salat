package org.tb.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.notification.domain.Notification;
import org.tb.notification.persistence.NotificationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Authorized
public class NotificationService {

    static final int BELL_LIMIT = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationRepository notificationRepository;
    private final AuthorizedEmployee authorizedEmployee;
    private final MessageSource messageSource;

    public void emitNotification(
            List<Long> recipientEmployeeIds,
            String titleKey,
            List<String> titleParams,
            String descriptionKey,
            List<String> descriptionParams,
            String actionUrl,
            String actionLabel) {
        validateKey(titleKey);
        for (Long employeeId : recipientEmployeeIds) {
            Notification n = new Notification();
            n.setRecipientEmployeeId(employeeId);
            n.setTitleKey(titleKey);
            n.setTitleParams(toJson(titleParams));
            n.setDescriptionKey(descriptionKey);
            n.setDescriptionParams(toJson(descriptionParams));
            n.setActionUrl(actionUrl);
            n.setActionLabel(actionLabel);
            n.setRead(false);
            notificationRepository.save(n);
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> getLatestForCurrentUser() {
        return notificationRepository.findByRecipientEmployeeIdOrderByCreatedDesc(
                authorizedEmployee.getEmployeeId(), PageRequest.of(0, BELL_LIMIT));
    }

    @Transactional(readOnly = true)
    public List<Notification> getAllForCurrentUser() {
        return notificationRepository.findByRecipientEmployeeIdOrderByCreatedDesc(
                authorizedEmployee.getEmployeeId());
    }

    @Transactional(readOnly = true)
    public long countUnreadForCurrentUser() {
        return notificationRepository.countByRecipientEmployeeIdAndReadFalse(
                authorizedEmployee.getEmployeeId());
    }

    public void markAllReadForCurrentUser() {
        notificationRepository.markAllReadByRecipientEmployeeId(authorizedEmployee.getEmployeeId());
    }

    public void deleteNotification(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new InvalidDataException(ErrorCode.XX_DATA_MISSING));
        if (!n.getRecipientEmployeeId().equals(authorizedEmployee.getEmployeeId())) {
            throw new AuthorizationException(ErrorCode.AA_NOT_ATHORIZED);
        }
        notificationRepository.deleteById(id);
    }

    public void deleteAllForCurrentUser() {
        notificationRepository.deleteByRecipientEmployeeId(authorizedEmployee.getEmployeeId());
    }

    private void validateKey(String key) {
        String resolved = messageSource.getMessage(key, null, null, Locale.GERMAN);
        if (resolved == null) {
            log.warn("Notification title key '{}' not found in message bundles", key);
        }
    }

    private String toJson(List<String> params) {
        if (params == null || params.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize notification params", e);
            return null;
        }
    }

}
