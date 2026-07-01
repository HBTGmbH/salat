package org.tb.notification.viewhelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.tb.notification.domain.Notification;

@Slf4j
public record NotificationViewHelper(
        Long id,
        String title,
        String description,
        String actionUrl,
        String actionLabel,
        boolean read,
        LocalDateTime created) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    public static NotificationViewHelper from(Notification n, MessageSource messages) {
        String[] titleArgs = parseParams(n.getTitleParams());
        String title = resolveMessage(n.getTitleKey(), titleArgs, messages);

        String description = null;
        if (n.getDescriptionKey() != null) {
            String[] descArgs = parseParams(n.getDescriptionParams());
            description = resolveMessage(n.getDescriptionKey(), descArgs, messages);
        }

        return new NotificationViewHelper(
                n.getId(),
                title,
                description,
                n.getActionUrl(),
                n.getActionLabel(),
                Boolean.TRUE.equals(n.getRead()),
                n.getCreated());
    }

    private static String[] parseParams(String json) {
        if (json == null || json.isBlank()) return new String[0];
        try {
            List<String> list = OBJECT_MAPPER.readValue(json, STRING_LIST);
            return list.toArray(String[]::new);
        } catch (Exception e) {
            log.warn("Failed to parse notification params: {}", json, e);
            return new String[0];
        }
    }

    private static String resolveMessage(String key, String[] args, MessageSource messages) {
        try {
            return messages.getMessage(key, args, Locale.GERMAN);
        } catch (Exception e) {
            log.warn("Could not resolve notification message key '{}'", key);
            return key;
        }
    }

}
