package de.hbt.salat.favorites.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.favorites.domain.Favorite;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long>,
    JpaSpecificationExecutor<Favorite> {

  /** The favourites of a person, found through their employee orders (#1369) — one query. */
  @Query("select f from Favorite f where f.employeeorder.employeecontract.employee.id = :employeeId")
  List<Favorite> findAllByEmployeeId(@Param("employeeId") long employeeId);
}
