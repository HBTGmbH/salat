package org.tb.auth.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.auth.domain.AuthorizationRule;

@Repository
public interface AuthorizationRuleRepository
    extends PagingAndSortingRepository<AuthorizationRule, Long>, CrudRepository<AuthorizationRule, Long> {
}
