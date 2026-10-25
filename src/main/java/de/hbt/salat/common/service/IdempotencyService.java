package de.hbt.salat.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.common.domain.IdempotencyEntry;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.persistence.IdempotencyEntryRepository;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.web.LoginSignProvider;

/**
 * Runs a creating call once per key (#1532, concept 6.5). The key is written in the same transaction as what the
 * call creates: if creating fails, there is no key, and a repetition may try again; if it succeeds, a repetition
 * with the same key and the same input answers with the id created then. The same key with another input is the
 * caller's mistake and refused. A key belongs to the login that used it, so nobody uses up another's key or learns
 * what another created.
 *
 * <p>Knows no business module: the caller names the operation and hands over the input and the creating call.
 * Not guarded by {@code @Authorized} — {@code common} imports no {@code auth} —, but refuses a call without a
 * login; who may create what is the creating service's answer.
 */
@Slf4j
@Service
public class IdempotencyService {

  /** The length of the column; a longer key would end in a constraint violation instead of an answer. */
  public static final int MAX_KEY_LENGTH = 100;

  private final IdempotencyEntryRepository repository;
  private final LoginSignProvider loginSignProvider;
  private final TransactionTemplate transactionTemplate;

  public IdempotencyService(IdempotencyEntryRepository repository, LoginSignProvider loginSignProvider,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.loginSignProvider = loginSignProvider;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * @param operation what is created, e.g. {@code createTimereport}
   * @param input     what makes two calls the same, in a canonical form compared through its {@code toString}: a
   *                  list of plain values in a fixed order, with an absent optional value in the form it defaults to.
   *                  A new optional argument enters it only where it changes what is created, and then so that an
   *                  input without it keeps its hash — otherwise a repetition across a deployment is refused.
   * @param create    creates and returns the id; it runs in the transaction the key is written in
   */
  public Outcome createOnce(String operation, String idempotencyKey, Object input, LongSupplier create) {
    if (idempotencyKey == null || idempotencyKey.isEmpty() || idempotencyKey.length() > MAX_KEY_LENGTH) {
      throw new InvalidDataException(ErrorCode.XX_IDEMPOTENCY_KEY_INVALID, MAX_KEY_LENGTH);
    }
    Long userId = loginSignProvider.getEffectiveUserId();
    if (userId == null) {
      throw new IllegalStateException("An idempotency key needs a login");
    }
    String inputHash = hash(operation, input);
    try {
      return transactionTemplate.execute(status -> repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
          .map(entry -> replay(entry, operation, inputHash))
          .orElseGet(() -> {
            long id = create.getAsLong();
            repository.save(new IdempotencyEntry(userId, idempotencyKey, operation, inputHash, id, ClockProvider.now()));
            return new Outcome(id, false);
          }));
    } catch (DataIntegrityViolationException concurrent) {
      // a call with the same key committed in between; this one is rolled back with what it created
      log.info("Idempotency key {} of login {} used concurrently, answering with the first call", idempotencyKey, userId);
      return transactionTemplate.execute(status -> repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
          .map(entry -> replay(entry, operation, inputHash))
          .orElseThrow(() -> concurrent));
    }
  }

  private static Outcome replay(IdempotencyEntry entry, String operation, String inputHash) {
    if (!entry.getOperation().equals(operation) || !entry.getInputHash().equals(inputHash)) {
      throw new BusinessRuleException(ErrorCode.XX_IDEMPOTENCY_KEY_REUSED);
    }
    return new Outcome(entry.getResultId(), true);
  }

  static String hash(String operation, Object input) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((operation + "\n" + input).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** @param replayed whether the answer comes from an earlier call with the same key */
  public record Outcome(long id, boolean replayed) {
  }
}
