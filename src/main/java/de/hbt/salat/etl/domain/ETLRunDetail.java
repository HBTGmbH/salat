package de.hbt.salat.etl.domain;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Ein Lauf mit einer Seite der Ausführungen, die die anfragende Person lesen darf (#1324).
 *
 * <p>Ein Lauf über die ganze Historie führt jede Definition für jeden Monat aus, das sind mehr als
 * zehntausend Ausführungen. Die Seite zeigt sie deshalb seitenweise und gefiltert.
 *
 * @param executions die gezeigte Seite der lesbaren Ausführungen, in der Reihenfolge, in der sie
 *     liefen, jeweils mit ihrer Nummer im Lauf
 * @param definitions die lesbaren Definitionen des Laufs, nach Namen geordnet — die Auswahl des
 *     Filters
 * @param hiddenExecutions wie viele Ausführungen desselben Laufs ihr wegen des fehlenden
 *     Leserechts nicht gezeigt werden. Ohne die Zahl sähe ein Lauf, dessen Ausführungen alle
 *     ausgeblendet sind, aus wie einer ohne Ausführungen — und die Seite behauptete etwas Falsches.
 *     Mehr als die Sammelmeldung des Laufs verrät die Zahl nicht: die nennt die Namen aller
 *     Definitionen, die gelaufen sind.
 */
public record ETLRunDetail(ETLRunHistory run, Page<ExecutionInRun> executions,
                           List<ETLDefinitionInRun> definitions, long hiddenExecutions) {

  public ETLRunDetail {
    definitions = List.copyOf(definitions);
  }

  /**
   * Eine Ausführung mit ihrer Nummer im ganzen Lauf, ab 1. Die Nummer hängt nicht am Filter: eine
   * Ausführung behält sie, gleich welche anderen gerade ausgeblendet sind.
   */
  public record ExecutionInRun(int position, ETLExecutionHistory execution) {
  }

  /** Alle lesbaren Ausführungen des Laufs, ohne Filter. */
  public long readableExecutions() {
    return definitions.stream().mapToLong(ETLDefinitionInRun::executions).sum();
  }

  /** Die fehlgeschlagenen unter den lesbaren Ausführungen, ohne Filter. */
  public long failedExecutions() {
    return definitions.stream().mapToLong(ETLDefinitionInRun::failed).sum();
  }

}
