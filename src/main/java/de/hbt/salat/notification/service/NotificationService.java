package de.hbt.salat.notification.service;

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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.notification.domain.Notification;
import de.hbt.salat.notification.persistence.NotificationRepository;
import de.hbt.salat.notification.persistence.RecipientReferences;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Authorized
public class NotificationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationRepository notificationRepository;
    private final SalatUserRepository salatUserRepository;
    private final RecipientReferences recipientReferences;
    private final AuthorizedUser authorizedUser;
    private final MessageSource messageSource;
    private final SalatProperties salatProperties;

    public void emitNotification(
            List<Long> recipientUserIds,
            String titleKey,
            List<String> titleParams,
            String descriptionKey,
            List<String> descriptionParams,
            String actionUrl,
            String actionLabel) {
        validateKey(titleKey);
        for (Long userId : recipientUserIds) {
            Notification n = new Notification();
            n.setRecipient(recipientReferences.salatUser(userId));
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
        return notificationRepository.findByRecipientIdOrderByCreatedDesc(
                currentUserId(), PageRequest.of(0, salatProperties.getNotifications().getBellLimit()));
    }

    @Transactional(readOnly = true)
    public List<Notification> getAllForCurrentUser() {
        return notificationRepository.findByRecipientIdOrderByCreatedDesc(currentUserId());
    }

    @Transactional(readOnly = true)
    public long countUnreadForCurrentUser() {
        return notificationRepository.countByRecipientIdAndReadFalse(currentUserId());
    }

    public void markAllReadForCurrentUser() {
        notificationRepository.markAllReadByRecipientId(currentUserId());
    }

    public void deleteNotification(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new InvalidDataException(ErrorCode.XX_DATA_MISSING));
        if (!n.getRecipientUserId().equals(currentUserId())) {
            throw new AuthorizationException(ErrorCode.AA_NOT_ATHORIZED);
        }
        notificationRepository.deleteById(id);
    }

    public void deleteAllForCurrentUser() {
        notificationRepository.deleteByRecipientId(currentUserId());
    }

    private Long currentUserId() {
        return salatUserRepository.findByLoginname(authorizedUser.getEffectiveLoginSign())
                .orElseThrow(() -> new InvalidDataException(ErrorCode.SE_USER_NOT_FOUND))
                .getId();
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
