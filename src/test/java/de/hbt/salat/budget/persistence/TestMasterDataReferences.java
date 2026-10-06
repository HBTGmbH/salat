package de.hbt.salat.budget.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;

/**
 * {@link MasterDataReferences} for tests whose repositories are mocks (#1367): a reference is a new
 * entity that carries only its id, which is all the services take from it.
 */
public final class TestMasterDataReferences {

  private TestMasterDataReferences() {
  }

  public static MasterDataReferences create() {
    var entityManager = mock(EntityManager.class);
    when(entityManager.getReference(any(Class.class), any())).thenAnswer(invocation -> {
      Object entity = BeanUtils.instantiateClass(invocation.<Class<?>>getArgument(0));
      setField(entity, "id", invocation.getArgument(1));
      return entity;
    });
    return new MasterDataReferences(entityManager);
  }
}
