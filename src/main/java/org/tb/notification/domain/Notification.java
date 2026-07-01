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

    @Column(nullable = false)
    private Long recipientEmployeeId;

    @Column(nullable = false)
    private String titleKey;

    @Column(length = 2000)
    private String titleParams;

    @Column
    private String descriptionKey;

    @Column(length = 2000)
    private String descriptionParams;

    @Column(length = 2000)
    private String actionUrl;

    @Column(length = 500)
    private String actionLabel;

    @Column(nullable = false)
    private Boolean read = false;

}
