package org.tb.statistic.domain;

import static jakarta.persistence.AccessType.FIELD;
import static lombok.AccessLevel.PRIVATE;

import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Access(FIELD)
@Table(name = "statistic_value", indexes = {
    @Index(name = "idx_main", columnList = "category, key, object_id")
})
@NoArgsConstructor(access = PRIVATE)
public class StatisticValue implements Persistable<Long> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Getter
  private Long id;

  @Getter
  private String category;

  @Column(name = "`key`")
  @Getter
  private String key;

  @Getter
  @Column(name = "object_id")
  private long objectId;

  private long value;

  @Column(length = 4000)
  @Getter
  @Setter
  private String comment;

  public StatisticValue(String category, String key, long objectId, Duration value, String comment) {
    this.category = category;
    this.key = key;
    this.objectId = objectId;
    this.comment = comment;
    this.value = value.toMinutes();
  }

  public Duration getAsDuration() {
    return Duration.ofMinutes(value);
  }

  public void setValue(Duration value) {
    this.value = value.toMinutes();
  }

  @Override
  public boolean isNew() {
    return id == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (id == null) {
      return false;
    }
    StatisticValue that = (StatisticValue) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    if (id == null) {
      return 0;
    }
    return Objects.hash(id);
  }

}

// Corresponding DDL:

/*
CREATE TABLE statistic_value (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(255),
    key VARCHAR(255),
    object_id BIGINT,
    value BIGINT,
    comment VARCHAR(4000),
    INDEX idx_main (category, key, object_id)
);
*/
