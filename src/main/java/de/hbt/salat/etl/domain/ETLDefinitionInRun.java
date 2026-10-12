package de.hbt.salat.etl.domain;

/**
 * Eine Definition, wie oft sie in einem Lauf ausgeführt wurde (#1324) — für den Filter der Seite
 * eines Laufs und für die Zahl der Ausführungen, die die Person nicht lesen darf.
 *
 * @param etlName der Name beim Schreiben der Ausführungen; eine inzwischen umbenannte oder gelöschte
 *     Definition steht so da, wie sie im Lauf hieß
 * @param executions alle Ausführungen dieser Definition im Lauf, eine je Referenzperiode
 * @param failed davon die fehlgeschlagenen
 */
public record ETLDefinitionInRun(long etlId, String etlName, long executions, long failed) {
}
