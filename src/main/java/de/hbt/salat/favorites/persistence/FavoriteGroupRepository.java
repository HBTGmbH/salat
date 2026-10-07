package de.hbt.salat.favorites.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.favorites.domain.FavoriteGroup;

@Repository
public interface FavoriteGroupRepository extends JpaRepository<FavoriteGroup, Long> {

  /** The groups of a person in their order (#1414). */
  @Query("select g from FavoriteGroup g where g.employee.id = :employeeId order by g.position, g.id")
  List<FavoriteGroup> findAllByEmployeeId(@Param("employeeId") long employeeId);
}
