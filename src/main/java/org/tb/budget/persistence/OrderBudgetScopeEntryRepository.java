package org.tb.budget.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.OrderBudgetScopeEntry;

@Repository
public interface OrderBudgetScopeEntryRepository extends CrudRepository<OrderBudgetScopeEntry, Long> {}
