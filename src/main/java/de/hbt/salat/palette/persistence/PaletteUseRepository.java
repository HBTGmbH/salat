package de.hbt.salat.palette.persistence;

import de.hbt.salat.palette.domain.PaletteUse;
import de.hbt.salat.palette.domain.PaletteUseDay;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PaletteUseRepository extends JpaRepository<PaletteUse, Long> {

  /** Raises the count of the day's row by one; 0 when there is no row yet. */
  @Modifying
  @Query("update PaletteUse u set u.useCount = u.useCount + 1 where u.salatUser.id = :salatUserId and u.useDate = :useDate")
  int increment(long salatUserId, LocalDate useDate);

  @Query("""
      select new de.hbt.salat.palette.domain.PaletteUseDay(u.salatUser.id, u.useDate, u.useCount)
      from PaletteUse u order by u.salatUser.id, u.useDate
      """)
  List<PaletteUseDay> findAllDays();

  @Query("select coalesce(sum(u.useCount), 0) from PaletteUse u where u.salatUser.id = :salatUserId")
  long sumUses(long salatUserId);
}
