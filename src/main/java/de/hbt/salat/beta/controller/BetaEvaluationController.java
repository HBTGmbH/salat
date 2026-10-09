package de.hbt.salat.beta.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.service.BetaEvaluationService;

/** The evaluation of the betas under "System" (#1447): sums only, for management. */
@Controller
@RequestMapping("/beta/evaluation")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class BetaEvaluationController {

  private final BetaEvaluationService betaEvaluationService;

  @GetMapping
  public String show(Model model) {
    model.addAttribute("evaluations", betaEvaluationService.getEvaluations());
    return "beta/evaluation";
  }
}
