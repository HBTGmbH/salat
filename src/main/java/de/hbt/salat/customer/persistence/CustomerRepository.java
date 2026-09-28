package de.hbt.salat.customer.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.domain.CustomerSearchRow;

@Repository
public interface CustomerRepository extends PagingAndSortingRepository<Customer, Long>,
    CrudRepository<Customer, Long> {

  @Query("""
      select c from Customer c order by upper(c.shortname) asc
      """)
  List<Customer> findAllOrderByShortnameIgnoreCase();

  @Query("""
      select c from Customer c where (c.hide is null or c.hide = false) order by upper(c.shortname) asc
      """)
  List<Customer> findAllVisibleOrderByShortnameIgnoreCase();

  /**
   * Wie {@link #findAllVisibleOrderByShortnameIgnoreCase()}, behält aber den Auftraggeber mit
   * {@code keepId} auch dann, wenn er ausgeblendet ist. Ein {@code keepId} von {@code null} trifft
   * auf keine Zeile zu und ergibt damit genau die Liste der sichtbaren Auftraggeber.
   */
  @Query("""
      select c from Customer c where (c.hide is null or c.hide = false) or c.id = :keepId
      order by upper(c.shortname) asc
      """)
  List<Customer> findAllVisibleOrWithIdOrderByShortnameIgnoreCase(Long keepId);

  /**
   * Candidates for the object search of the command palette (#1157): every customer whose short
   * name or name contains each of the words, hidden ones included and ordered last — the palette
   * shows them marked. A missing word is {@code null} and no condition. {@code hide = true} is false
   * for {@code null}, the reading of {@link de.hbt.salat.common.Hiding}.
   */
  @Query("""
      select new de.hbt.salat.customer.domain.CustomerSearchRow(c.id, c.shortname, c.name, c.hide)
      from Customer c
      where (lower(c.shortname) like :word1 escape '!' or lower(c.name) like :word1 escape '!')
      and (:word2 is null or lower(c.shortname) like :word2 escape '!' or lower(c.name) like :word2 escape '!')
      and (:word3 is null or lower(c.shortname) like :word3 escape '!' or lower(c.name) like :word3 escape '!')
      order by case when c.hide = true then 1 else 0 end, upper(c.shortname)
      """)
  List<CustomerSearchRow> findPaletteCandidates(String word1, String word2, String word3, Pageable page);

}
