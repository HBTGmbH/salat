package org.tb.customer;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends PagingAndSortingRepository<Customer, Long>,
    CrudRepository<Customer, Long> {

  @Query("""
      select c from Customer c where cast(c.id as string) like upper(:filter) or upper(c.name) like upper(:filter) \
      or upper(c.address) like upper(:filter) or upper(c.shortname) like upper(:filter) order by c.name asc\
      """)
  List<Customer> findAllByFilterIgnoringCase(String filter);

}
