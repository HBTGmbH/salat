package de.hbt.salat.budget.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntry;

@Repository
public interface OrderBudgetScopeEntryRepository extends CrudRepository<OrderBudgetScopeEntry, Long> {}
