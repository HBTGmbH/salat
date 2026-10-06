package de.hbt.salat.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.domain.AuditedEntity;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends AuditedEntity {

    /**
     * The login the notification is for. A reference to master data of auth (#1370, ADR-0036): read
     * only, no cascade; the foreign key deletes the notification with its login.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private SalatUser recipient;

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

    /** The id of {@link #recipient}, read off the reference without loading the login. */
    public Long getRecipientUserId() {
        return recipient != null ? recipient.getId() : null;
    }

}
