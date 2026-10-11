package de.hbt.salat.etl.domain;

import java.util.List;

/**
 * Ein Lauf mit den Ausführungen, die die anfragende Person lesen darf (#1324).
 *
 * @param executions die lesbaren Ausführungen, in der Reihenfolge, in der sie liefen
 * @param hiddenExecutions wie viele Ausführungen desselben Laufs ihr wegen des fehlenden
 *     Leserechts nicht gezeigt werden. Ohne die Zahl sähe ein Lauf, dessen Ausführungen alle
 *     ausgeblendet sind, aus wie einer ohne Ausführungen — und die Seite behauptete etwas Falsches.
 *     Mehr als die Sammelmeldung des Laufs verrät die Zahl nicht: die nennt die Namen aller
 *     Definitionen, die gelaufen sind.
 */
public record ETLRunDetail(ETLRunHistory run, List<ETLExecutionHistory> executions, int hiddenExecutions) {
}
