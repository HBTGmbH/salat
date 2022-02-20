package org.tb.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Customer;

@Repository
public interface CustomerRepository extends PagingAndSortingRepository<Customer, Long>  {

  @Query("select c from Customer c where upper(c.id) like upper(:filter) or upper(c.name) like upper(:filter) "
      + "or upper(c.address) like upper(:filter) or upper(c.shortname) like upper(:filter) order by c.name asc")
  List<Customer> findAllByFilterIgnoringCase(String filter);

}
