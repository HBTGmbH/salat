package de.hbt.salat.etl.domain;

/**
 * Eine ETL-Definition, wie sie in der Auswahlliste des Anstoß-Formulars steht (#1071).
 *
 * <p>Keine Entität in der öffentlichen Schnittstelle des Dienstes: was die Oberfläche braucht, sind
 * Name und Beschreibung, nicht die drei SQL-Blöcke und der Abhängigkeitsgraph dahinter.
 *
 * @param id   die Id — das, was eine Berechtigungsregel der Kategorie {@code ETL} als Objekt trägt
 *     (#1204): ein Name lässt sich ändern, und die Regel ginge dann an eine andere Definition
 * @param name der Name, mit dem die Definition ausgewählt und ausgeführt wird
 */
public record ETLDefinitionOption(Long id, String name, String description) {

  public static ETLDefinitionOption from(ETLDefinition definition) {
    return new ETLDefinitionOption(definition.getId(), definition.getName(), definition.getDescription());
  }

}
