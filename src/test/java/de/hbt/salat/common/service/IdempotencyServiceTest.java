package de.hbt.salat.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static de.hbt.salat.common.exception.ErrorCode.XX_IDEMPOTENCY_KEY_INVALID;

import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.persistence.IdempotencyEntryRepository;
import de.hbt.salat.common.web.LoginSignProvider;

/** The idempotency key as the caller hands it (#1532): what the column holds, and a hash of the meaning. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class IdempotencyServiceTest {

  private final IdempotencyEntryRepository repository = mock(IdempotencyEntryRepository.class);
  private final LongSupplier create = mock(LongSupplier.class);
  private final IdempotencyService service = new IdempotencyService(repository, mock(LoginSignProvider.class),
      mock(PlatformTransactionManager.class));

  /** Longer than the column: refused with a code, instead of a constraint violation read as a concurrent call. */
  @ParameterizedTest
  @ValueSource(ints = {0, 101, 128})
  void a_key_of_this_length_is_refused_before_anything_is_created(int length) {
    assertThatThrownBy(() -> service.createOnce("createTimereport", "k".repeat(length), List.of(), create))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(XX_IDEMPOTENCY_KEY_INVALID));
    verifyNoInteractions(repository, create);
  }

  @Test
  void the_hash_depends_on_operation_and_input() {
    String hash = IdempotencyService.hash("createTimereport", List.of(1L, "Wartung"));

    assertThat(IdempotencyService.hash("createTimereport", List.of(1L, "Wartung"))).isEqualTo(hash).hasSize(64);
    assertThat(IdempotencyService.hash("createTimereport", List.of(1L, "Betrieb"))).isNotEqualTo(hash);
    assertThat(IdempotencyService.hash("createWorkingday", List.of(1L, "Wartung"))).isNotEqualTo(hash);
  }
}
