package org.tb.notification.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.tb.notification.domain.Notification;

public interface NotificationRepository extends PagingAndSortingRepository<Notification, Long>, CrudRepository<Notification, Long> {

    List<Notification> findByRecipientEmployeeIdOrderByCreatedDesc(Long recipientEmployeeId, Pageable pageable);

    List<Notification> findByRecipientEmployeeIdOrderByCreatedDesc(Long recipientEmployeeId);

    long countByRecipientEmployeeIdAndReadFalse(Long recipientEmployeeId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientEmployeeId = :employeeId")
    void markAllReadByRecipientEmployeeId(Long employeeId);

    void deleteByRecipientEmployeeId(Long employeeId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.created < :before")
    void deleteByCreatedBefore(LocalDateTime before);

}
