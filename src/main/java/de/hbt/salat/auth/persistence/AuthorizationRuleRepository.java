package de.hbt.salat.auth.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.auth.domain.AuthorizationRule;

@Repository
public interface AuthorizationRuleRepository
    extends PagingAndSortingRepository<AuthorizationRule, Long>, CrudRepository<AuthorizationRule, Long> {

  List<AuthorizationRule> findAllByNameIgnoreCase(String name);

}
