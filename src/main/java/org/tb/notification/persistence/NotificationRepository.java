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

    List<Notification> findByRecipientUserIdOrderByCreatedDesc(Long recipientUserId, Pageable pageable);

    List<Notification> findByRecipientUserIdOrderByCreatedDesc(Long recipientUserId);

    long countByRecipientUserIdAndReadFalse(Long recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientUserId = :userId")
    void markAllReadByRecipientUserId(Long userId);

    void deleteByRecipientUserId(Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.created < :before")
    void deleteByCreatedBefore(LocalDateTime before);

}
