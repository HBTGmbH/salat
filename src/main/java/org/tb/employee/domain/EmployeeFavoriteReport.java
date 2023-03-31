package org.tb.employee.domain;

import java.io.Serial;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.order.domain.Employeeorder;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Table(name = "employee_favorite_report")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class EmployeeFavoriteReport implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  // for discussion: I don't think this need to be audited
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "EMPLOYEE_ID")
  @Cascade(CascadeType.SAVE_UPDATE)
  private Employee employee;

  @ManyToOne
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "EMPLOYEEORDER_ID")
  private Employeeorder employeeorder;

  private Integer durationhours;
  private Integer durationminutes;
  @Lob
  @Column(columnDefinition = "text")
  private String description;

}
