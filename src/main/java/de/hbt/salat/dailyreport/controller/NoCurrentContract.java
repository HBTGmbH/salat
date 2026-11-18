package de.hbt.salat.dailyreport.controller;

import org.springframework.ui.Model;

/**
 * Die Seite für eine Anmeldung, die heute keinen gültigen Vertrag hat (#1559). Herein kommt so jede
 * Person, gleich mit welchem Status (#1588): die Rolle hängt am Login, nicht am Vertrag. Seiten, die
 * ohne gewählten Vertrag den eigenen zeigen, zeigen ihr diesen Hinweis statt eines Fehlers.
 */
final class NoCurrentContract {

  static final String VIEW = "dailyreport/no-current-contract";

  private NoCurrentContract() {
  }

  static String view(Model model, String pageTitle, String subSection) {
    model.addAttribute("pageTitle", pageTitle);
    model.addAttribute("section", "dailyreport");
    model.addAttribute("subSection", subSection);
    return VIEW;
  }
}
