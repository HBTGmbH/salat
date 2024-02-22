package org.tb.employee.domain;

import java.io.Serializable;
import java.time.Duration;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.AuditedEntity;
import org.tb.common.DurationMinutesConverter;
import org.tb.common.util.DateUtils;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Overtime extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(CascadeType.PERSIST)
    private Employeecontract employeecontract;

    private String comment;

    @Convert(converter = DurationMinutesConverter.class)
    private Duration timeMinutes;

    public String getCreatedString() {
        return DateUtils.formatDateTime(getCreated(), "yyyy-MM-dd HH:mm");
    }

    public void setTime(Duration duration) {
        this.timeMinutes = duration;
    }

}

