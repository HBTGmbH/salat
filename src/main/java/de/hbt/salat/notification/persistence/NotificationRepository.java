package de.hbt.salat.notification.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import de.hbt.salat.notification.domain.Notification;

public interface NotificationRepository extends PagingAndSortingRepository<Notification, Long>, CrudRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedDesc(Long recipientUserId, Pageable pageable);

    List<Notification> findByRecipientIdOrderByCreatedDesc(Long recipientUserId);

    long countByRecipientIdAndReadFalse(Long recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :userId")
    void markAllReadByRecipientId(Long userId);

    void deleteByRecipientId(Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.created < :before")
    void deleteByCreatedBefore(LocalDateTime before);

}
