package org.tb.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends AuditedEntity {

    @Column(nullable = false, name = "recipient_user_id")
    private Long recipientUserId;

    @Column(nullable = false, name = "title_key")
    private String titleKey;

    @Column(length = 2000, name = "title_params")
    private String titleParams;

    @Column(name = "description_key")
    private String descriptionKey;

    @Column(length = 2000, name = "description_params")
    private String descriptionParams;

    @Column(length = 2000, name = "action_url")
    private String actionUrl;

    @Column(length = 500, name = "action_label")
    private String actionLabel;

    @Column(nullable = false, name = "`read`")
    private Boolean read = false;

}
