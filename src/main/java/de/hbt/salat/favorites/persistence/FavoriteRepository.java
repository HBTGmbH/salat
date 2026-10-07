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

  /**
   * The favourites of a person, found through their employee orders (#1369) — one query. Suborder,
   * group and ticket references come along (#1414): the list shows all three for every favourite,
   * and reading them one by one would cost a query per favourite each.
   */
  @Query("""
      select f from Favorite f
        join fetch f.employeeorder eo
        join fetch eo.suborder
        left join fetch f.group
        left join fetch f.ticketReferences
       where eo.employeecontract.employee.id = :employeeId
      """)
  List<Favorite> findAllByEmployeeId(@Param("employeeId") long employeeId);

  /** The favourites of a group (#1414), for moving them out of it before it is deleted. */
  @Query("select f from Favorite f where f.group.id = :groupId")
  List<Favorite> findAllByGroupId(@Param("groupId") long groupId);

  /** The topmost place among a person's favourites without a group (#1414), {@code null} without one. */
  @Query("""
      select min(f.position) from Favorite f
       where f.group is null and f.employeeorder.employeecontract.employee.id = :employeeId
      """)
  Integer findMinPositionWithoutGroup(@Param("employeeId") long employeeId);

  /** The lowest place among a person's favourites without a group (#1414), {@code null} without one. */
  @Query("""
      select max(f.position) from Favorite f
       where f.group is null and f.employeeorder.employeecontract.employee.id = :employeeId
      """)
  Integer findMaxPositionWithoutGroup(@Param("employeeId") long employeeId);

  /** The topmost place in a group (#1414), {@code null} while nothing in it is placed. */
  @Query("select min(f.position) from Favorite f where f.group.id = :groupId")
  Integer findMinPositionInGroup(@Param("groupId") long groupId);
}
