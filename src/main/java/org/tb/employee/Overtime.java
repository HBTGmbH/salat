package org.tb.employee;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.io.Serializable;
import java.math.BigDecimal;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.AuditedEntity;
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
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employeecontract employeecontract;

    private String comment;
    private int timeMinutes;

    public String getCreatedString() {
        return DateUtils.formatDateTime(getCreated(), "yyyy-MM-dd HH:mm");
    }

    public Double getTime() {
        return BigDecimal
            .valueOf(timeMinutes)
            .setScale(2)
            .divide(BigDecimal.valueOf(MINUTES_PER_HOUR))
            .doubleValue();
    }

    public void setTime(Double value) {
        timeMinutes = BigDecimal
            .valueOf(value)
            .setScale(2)
            .multiply(BigDecimal.valueOf(MINUTES_PER_HOUR))
            .setScale(0)
            .intValue();
    }

}

