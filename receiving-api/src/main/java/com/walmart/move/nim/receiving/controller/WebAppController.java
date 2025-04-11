package com.walmart.move.nim.receiving.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** @author sks0013 */
@Controller
@RequestMapping("receiving-web/**")
@Tag(
    name = "Web app Service",
    description = "To expose receiving web app resource and related services")
public class WebAppController {
  /**
   * Serving search tool html page
   *
   * @param model
   * @return
   */
  @GetMapping(path = "", produces = "application/json")
  public String viewSearchToolPage(final Model model) {
    return "index";
  }
}
