package org.tb.dailyreport.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;

@Controller
@RequestMapping("/dailyreport/mockup")
@Authorized
public class DailyReportMockupController {

    @GetMapping
    public String index() {
        return "dailyreport/mockup/index";
    }

    @GetMapping("/a/daily")
    public String variantADaily() {
        return "dailyreport/mockup/variant-a-daily";
    }

    @GetMapping("/a/matrix")
    public String variantAMatrix() {
        return "dailyreport/mockup/variant-a-matrix";
    }

    @GetMapping("/b/daily")
    public String variantBDaily() {
        return "dailyreport/mockup/variant-b-daily";
    }

    @GetMapping("/b/form")
    public String variantBForm() {
        return "dailyreport/mockup/variant-b-form";
    }

    @GetMapping("/b/calendar")
    public String variantBCalendar() {
        return "dailyreport/mockup/variant-b-calendar";
    }

    @GetMapping("/b/data")
    public String variantBData() {
        return "dailyreport/mockup/variant-b-data";
    }

    @GetMapping("/c/daily")
    public String variantCDaily() {
        return "dailyreport/mockup/variant-c-daily";
    }

    @GetMapping("/c/calendar")
    public String variantCCalendar() {
        return "dailyreport/mockup/variant-c-calendar";
    }

    @GetMapping("/c/favourites")
    public String variantCFavourites() {
        return "dailyreport/mockup/variant-c-favourites";
    }

    @GetMapping("/c/data")
    public String variantCData() {
        return "dailyreport/mockup/variant-c-data";
    }
}
