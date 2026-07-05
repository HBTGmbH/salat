package org.tb.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "customer_segment")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CustomerSegment extends AuditedEntity {

    @Column(nullable = false, unique = true)
    private String name;

}
