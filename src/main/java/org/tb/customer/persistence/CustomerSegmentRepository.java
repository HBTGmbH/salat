package org.tb.customer.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.customer.domain.CustomerSegment;

@Repository
public interface CustomerSegmentRepository
    extends CrudRepository<CustomerSegment, Long>, PagingAndSortingRepository<CustomerSegment, Long> {

    List<CustomerSegment> findAllByOrderByNameAsc();

}
