package org.tb.order.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.order.domain.Customerorder;

@Repository
public interface CustomerorderRepository extends PagingAndSortingRepository<Customerorder, Long>,
    JpaSpecificationExecutor<Customerorder>, CrudRepository<Customerorder, Long> {

  @Query("select c from Customerorder c where c.responsible_hbt.id = :responsibleHbtId")
  List<Customerorder> findAllByResponsibleHbt(long responsibleHbtId);

  List<Customerorder> findAllByCustomerId(long customerId);

  Optional<Customerorder> findBySign(String sign);

  @Query("""
      select c from Customerorder c where (c.hide is null or c.hide = false)
      or (c.fromDate <= :date and (c.untilDate is null or c.untilDate >= :date))
      order by c.sign
  """)
  List<Customerorder> findAllValidAtAndNotHidden(LocalDate date);

  @Query("""
      select distinct c from Customerorder c inner join fetch c.suborders s where s.invoice = 'Y'
      order by c.sign
  """)
  List<Customerorder> findAllInvoiceable();

}
