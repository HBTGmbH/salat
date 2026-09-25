package org.tb.common.viewhelper;

import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

/**
 * Version, Bauzeitpunkt und Commit-Kürzel für die Fußleiste (#1117).
 *
 * <p>{@link BuildProperties} und {@link GitProperties} sind <em>bedingte</em> Bohnen: Spring Boot
 * legt sie nur an, wenn {@code META-INF/build-info.properties} bzw. {@code git.properties} im
 * Klassenpfad liegen, und beide Dateien entstehen erst im Maven-Lauf. Fehlt eine davon, gibt es die
 * Bohne nicht — und {@code ${@gitProperties.shortCommitId}} im Template bricht die Seite mit einer
 * {@code NoSuchBeanDefinitionException} ab. Weil die Stelle in der Fußleiste steht, ist die Antwort
 * dann schon fast vollständig geschrieben: Der Server bricht mitten im Chunked-Strom ab, und der
 * Browser bekommt eine abgeschnittene Seite ohne jeden Hinweis darauf, was los ist.
 *
 * <p>Ein {@code th:if} auf die Bohne hilft dagegen nicht — {@code ${@gitProperties != null}} löst
 * sie genauso auf und bricht genauso ab. Die Fallunterscheidung muss also aus dem Template heraus,
 * und sie liegt hier genauso, wie sie für die OpenAPI-Konfiguration schon liegt: als
 * {@link Optional} im Konstruktor. Diese Bohne gibt es immer; das Template liest nur noch sie und
 * bekommt für eine fehlende Angabe einen Leerstring.
 */
@Component
public class BuildInfoViewHelper {

  private final String version;
  private final String buildTime;
  private final String commitId;

  public BuildInfoViewHelper(Optional<BuildProperties> buildProperties, Optional<GitProperties> gitProperties) {
    // map() faengt auch die halb gefuellte Datei ab: die Getter duerfen null liefern.
    this.version = buildProperties.map(BuildProperties::getVersion).orElse("");
    this.buildTime = buildProperties.map(BuildProperties::getTime).map(Object::toString).orElse("");
    this.commitId = gitProperties.map(GitProperties::getShortCommitId).orElse("");
  }

  /** Die Version aus {@code build-info.properties}, sonst der Leerstring. */
  public String getVersion() {
    return version;
  }

  /** Der Bauzeitpunkt aus {@code build-info.properties}, sonst der Leerstring. */
  public String getBuildTime() {
    return buildTime;
  }

  /** Das Commit-Kürzel aus {@code git.properties}, sonst der Leerstring. */
  public String getCommitId() {
    return commitId;
  }

}
