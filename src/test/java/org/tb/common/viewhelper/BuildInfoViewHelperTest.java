package org.tb.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

/**
 * Die drei Angaben der Fußleiste (#1117). Beide Quellen sind bedingt, also ist jede Kombination aus
 * „vorhanden" und „fehlt" ein Fall, der vorkommt — und keiner davon darf eine Ausnahme sein.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BuildInfoViewHelperTest {

  private static final Instant BUILD_TIME = Instant.parse("2025-09-25T08:15:30Z");

  @Test
  public void should_show_all_three_values_when_both_sources_are_there() {
    var viewHelper = new BuildInfoViewHelper(Optional.of(buildProperties()), Optional.of(gitProperties()));

    assertThat(viewHelper.getVersion()).isEqualTo("5.0.10");
    assertThat(viewHelper.getBuildTime()).isEqualTo(BUILD_TIME.toString());
    assertThat(viewHelper.getCommitId()).isEqualTo("abc1234");
  }

  /** Der Fall eines Builds ohne {@code git-commit-id-plugin} — bis #1117 brach daran jede Seite ab. */
  @Test
  public void should_leave_out_the_commit_id_when_the_git_properties_are_missing() {
    var viewHelper = new BuildInfoViewHelper(Optional.of(buildProperties()), Optional.empty());

    assertThat(viewHelper.getVersion()).isEqualTo("5.0.10");
    assertThat(viewHelper.getBuildTime()).isEqualTo(BUILD_TIME.toString());
    assertThat(viewHelper.getCommitId()).isEmpty();
  }

  @Test
  public void should_leave_out_version_and_build_time_when_the_build_properties_are_missing() {
    var viewHelper = new BuildInfoViewHelper(Optional.empty(), Optional.of(gitProperties()));

    assertThat(viewHelper.getVersion()).isEmpty();
    assertThat(viewHelper.getBuildTime()).isEmpty();
    assertThat(viewHelper.getCommitId()).isEqualTo("abc1234");
  }

  @Test
  public void should_answer_with_empty_texts_when_neither_source_is_there() {
    var viewHelper = new BuildInfoViewHelper(Optional.empty(), Optional.empty());

    assertThat(viewHelper.getVersion()).isEmpty();
    assertThat(viewHelper.getBuildTime()).isEmpty();
    assertThat(viewHelper.getCommitId()).isEmpty();
  }

  /**
   * Eine vorhandene Datei heißt nicht, dass sie alles enthält: die Getter von
   * {@link BuildProperties} liefern für einen fehlenden Eintrag {@code null}.
   */
  @Test
  public void should_answer_with_empty_texts_when_the_files_are_there_but_empty() {
    var viewHelper = new BuildInfoViewHelper(
        Optional.of(new BuildProperties(new Properties())),
        Optional.of(new GitProperties(new Properties())));

    assertThat(viewHelper.getVersion()).isEmpty();
    assertThat(viewHelper.getBuildTime()).isEmpty();
    assertThat(viewHelper.getCommitId()).isEmpty();
  }

  private BuildProperties buildProperties() {
    var properties = new Properties();
    properties.setProperty("version", "5.0.10");
    properties.setProperty("time", String.valueOf(BUILD_TIME.toEpochMilli()));
    return new BuildProperties(properties);
  }

  private GitProperties gitProperties() {
    var properties = new Properties();
    properties.setProperty("commit.id.abbrev", "abc1234");
    return new GitProperties(properties);
  }

}
