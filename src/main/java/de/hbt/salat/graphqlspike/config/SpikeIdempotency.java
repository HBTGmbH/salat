package de.hbt.salat.graphqlspike.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;

/**
 * Idempotency keys for creating mutations (concept 6.5), kept in memory instead of a table: the spike adds no
 * changeset, so that deleting it leaves nothing behind. It shows the behaviour, not the guarantees — a restart
 * forgets every key, nothing expires, and a key is not written in the transaction of what it created.
 *
 * <p>A key belongs to the login that used it. The same key with the same input answers with the id created the
 * first time; with another input it is refused.
 */
@Component
@Profile(GraphqlSpikeConfiguration.PROFILE)
@RequiredArgsConstructor
public class SpikeIdempotency {

  private final AuthorizedUser authorizedUser;
  private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

  /**
   * @param input  compared by {@code equals} — a record of the arguments
   * @param create runs once per key and returns the id of what it created
   */
  public Outcome createOnce(String operation, String idempotencyKey, Object input, LongSupplier create) {
    var key = new Key(authorizedUser.getLoginSign(), idempotencyKey);
    var created = new boolean[1];
    // computeIfAbsent holds the bin while creating: a second call with the same key waits and then sees the first
    var entry = entries.computeIfAbsent(key, k -> {
      created[0] = true;
      return new Entry(operation, input, create.getAsLong());
    });
    if (!entry.operation().equals(operation) || !entry.input().equals(input)) {
      throw new SpikeException(SpikeException.IDEMPOTENCY_CONFLICT, SpikeException.CONFLICT,
          "The idempotency key was already used with another input");
    }
    return new Outcome(entry.resultId(), !created[0]);
  }

  public record Outcome(long id, boolean replayed) {
  }

  private record Key(String loginSign, String idempotencyKey) {
  }

  private record Entry(String operation, Object input, long resultId) {
  }
}
