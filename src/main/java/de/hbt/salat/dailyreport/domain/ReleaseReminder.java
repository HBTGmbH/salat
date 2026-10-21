package de.hbt.salat.dailyreport.domain;

import de.hbt.salat.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * That the person of a contract was reminded automatically to release on one day (#215). The bell
 * and the mail are no record of it: notifications expire, and the person may delete them. The row
 * keeps a second run on the same day from mailing again, and the acceptance shows the last date
 * beside the button that reminds by hand.
 *
 * <p>The contract is referenced by id: it is master data of {@code employee}, and nothing here
 * navigates to it. Deleting the contract deletes its rows ({@code ReleaseReminderService}).
 */
@Entity
@Table(name = "release_reminder", uniqueConstraints = @UniqueConstraint(
    name = "uk_release_reminder_employeecontract_id_reminded_on", columnNames = {"employeecontract_id", "reminded_on"}))
@Getter
@Setter
@NoArgsConstructor
public class ReleaseReminder extends AuditedEntity {

  @Column(name = "employeecontract_id", nullable = false)
  private Long employeecontractId;

  @Column(name = "reminded_on", nullable = false)
  private LocalDate remindedOn;

  public ReleaseReminder(long employeecontractId, LocalDate remindedOn) {
    this.employeecontractId = employeecontractId;
    this.remindedOn = remindedOn;
  }
}
