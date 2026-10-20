package de.hbt.salat.achievement.viewhelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hbt.salat.achievement.domain.AchievementQuote;
import de.hbt.salat.common.util.ClockProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Ten quotes per level of every achievement (#1501) - from films, series, music and books, the
 * nerdier the better: the people reading them write software. The popup shows one of them at
 * random with each new level. They stand in the original language, since a translated line loses
 * what makes it recognizable.
 *
 * <p>Read once from {@code achievement/quotes.json}, keyed by achievement and level;
 * {@code AchievementQuotesTest} checks that every contributed level has its ten.
 */
@Component
public class AchievementQuotes {

  static final String RESOURCE = "/achievement/quotes.json";

  private final Map<String, Map<String, List<AchievementQuote>>> quotes;

  public AchievementQuotes() {
    this.quotes = load();
  }

  /** All quotes of the level; empty where there are none. */
  public List<AchievementQuote> of(String key, int level) {
    return quotes.getOrDefault(key, Map.of()).getOrDefault(String.valueOf(level), List.of());
  }

  /**
   * One of the level's quotes, as good as random: picked by the time of day down to the
   * nanosecond, mixed with achievement and level so that two levels on one page differ. The time
   * comes from {@link ClockProvider}, so a test with a fixed clock gets a fixed quote - true
   * randomness is not allowed here ({@code ArchitectureTest.useDeterministicRandomness}).
   */
  public Optional<AchievementQuote> random(String key, int level) {
    var candidates = of(key, level);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    long seed = ClockProvider.now().toLocalTime().toNanoOfDay() + 31L * key.hashCode() + 17L * level;
    return Optional.of(candidates.get((int) Math.floorMod(seed, candidates.size())));
  }

  static Map<String, Map<String, List<AchievementQuote>>> load() {
    try (InputStream in = AchievementQuotes.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("missing " + RESOURCE);
      }
      return new ObjectMapper().readValue(in, new TypeReference<>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("unreadable " + RESOURCE, e);
    }
  }
}
