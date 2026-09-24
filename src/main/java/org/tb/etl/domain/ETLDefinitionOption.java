package org.tb.etl.domain;

/**
 * Eine ETL-Definition, wie sie in der Auswahlliste des Anstoß-Formulars steht (#1071).
 *
 * <p>Keine Entität in der öffentlichen Schnittstelle des Dienstes: was die Oberfläche braucht, sind
 * Name und Beschreibung, nicht die drei SQL-Blöcke und der Abhängigkeitsgraph dahinter.
 *
 * @param name der Name, mit dem die Definition auch geprüft und ausgeführt wird — genau die
 *     Zeichenkette, die eine Berechtigungsregel der Kategorie {@code ETL} als Objekt trägt
 */
public record ETLDefinitionOption(String name, String description) {

  public static ETLDefinitionOption from(ETLDefinition definition) {
    return new ETLDefinitionOption(definition.getName(), definition.getDescription());
  }

}
