package de.hbt.salat.achievement.persistence;

import de.hbt.salat.achievement.domain.Achievement;
import de.hbt.salat.achievement.domain.RecordedLevel;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

  @Query("""
      select new de.hbt.salat.achievement.domain.RecordedLevel(a.employee.id, a.achievementKey, a.achievementLevel)
      from Achievement a
      """)
  List<RecordedLevel> findAllRecordedLevels();

  @Query("select a from Achievement a where a.employee.id = :employeeId order by a.achievedOn, a.achievementLevel")
  List<Achievement> findAllByEmployeeId(long employeeId);

  @Query("""
      select a from Achievement a where a.employee.id = :employeeId and a.seenAt is null
      order by a.achievedOn, a.achievementLevel
      """)
  List<Achievement> findUnseenByEmployeeId(long employeeId);

  /** Asked on every full page: answered from the unique key, whose first column is the person. */
  @Query("select count(a) > 0 from Achievement a where a.employee.id = :employeeId and a.seenAt is null")
  boolean existsUnseenByEmployeeId(long employeeId);

  @Modifying
  @Query("update Achievement a set a.seenAt = :seenAt where a.employee.id = :employeeId and a.seenAt is null")
  int markSeen(long employeeId, LocalDateTime seenAt);
}
