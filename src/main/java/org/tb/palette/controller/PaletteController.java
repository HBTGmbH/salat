package org.tb.palette.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.domain.Authorized;
import org.tb.palette.service.PaletteSearchService;

/**
 * The object search of the command palette (#1157): the hits of every module for what was typed, as
 * a fragment that {@code salat.js} merges into the palette's list.
 *
 * <p>No {@code requireUnrestricted}: externals and interns use the palette as well. What each of them
 * finds is decided by the providers, per hit and per target — a restricted user finds the suborders
 * they may book and their own contract, nothing of the master data they cannot open.
 */
@Controller
@RequestMapping("/palette")
@RequiredArgsConstructor
@Authorized
public class PaletteController {

  private final PaletteSearchService paletteSearchService;

  @GetMapping("/search")
  public String search(@RequestParam(defaultValue = "") String q, Model model) {
    model.addAttribute("groups", paletteSearchService.search(q));
    return "palette/search-results :: results";
  }
}
