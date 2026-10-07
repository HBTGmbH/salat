package de.hbt.salat.dailyreport.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_INVALID_FORMAT;
import static de.hbt.salat.common.util.DateUtils.formatMonth;
import static de.hbt.salat.common.util.DateUtils.today;
import static de.hbt.salat.common.util.DurationUtils.parseFlexibleMinutes;
import static de.hbt.salat.common.util.TimeFormatUtils.parseFlexibleTimeOfDay;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.domain.PreviousBooking;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.MatrixService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;
import java.time.Duration;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.viewhelper.TicketReferencePolicyViewHelper;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;

@Controller
@RequestMapping("/dailyreport/daily")
@RequiredArgsConstructor
@Authorized
public class DailyController {

    private final DailyService dailyService;
    private final MatrixService matrixService;
    private final TimereportService timereportService;
    private final WorkingdayService workingdayService;
    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final AuthorizedEmployee authorizedEmployee;
    private final FavoriteService favoriteService;
    private final EmployeeorderService employeeorderService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final DailyPreferenceService dailyPreferenceService;
    private final TicketReferencePolicyViewHelper ticketReferencePolicyViewHelper;

    /**
     * The daily or the list view.
     *
     * <p>A day opened from an overview before a release keeps the way back while it is worked on
     * (#760): through the saving of start and break, through the HTMX refreshes of its bookings
     * ({@link #reviewReturnUrlOf}), through deleting a booking and through the booking form, whose
     * return target leads back into this day with the overview's address. Navigating to another
     * day drops it — the overview's day links lead to one day each.
     *
     * @param returnUrl where the view was opened from. Only an overview before a release gets a way
     *                  back (#760): its days lead here, and the start of work and the break that its
     *                  findings name are corrected here. Anything else is dropped ({@link ReturnUrls}).
     */
    @GetMapping
    public String show(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer fMonth,
            @RequestParam(required = false) Integer fYear,
            @RequestParam(required = false) String returnUrl,
            Model model) {

        var today = today();
        long ecId = effectiveContractId(fEmployeeContractId);
        model.addAttribute("reviewReturnUrl", ReturnUrls.isReviewPage(returnUrl) ? returnUrl : null);

        String effectiveMode = (mode != null && mode.equals("list")) ? "list" : "daily";

        model.addAttribute("selectedContractId", ecId);
        if (ecId > 0) {
            var ec = employeecontractService.getEmployeecontractById(ecId);
            if (ec != null) {
                model.addAttribute("selectedEmployeeName", EmployeeLabelViewHelper.title(ec));
            }
        }
        model.addAttribute("mode", effectiveMode);
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "daily");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.daily.text"));

        LocalDate targetDate = date != null ? date : today;

        if ("list".equals(effectiveMode)) {
            int targetMonth = fMonth != null ? fMonth : today.getMonthValue();
            int targetYear = fYear != null ? fYear : today.getYear();
            YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);
            YearMonth prev = yearMonth.minusMonths(1);
            YearMonth next = yearMonth.plusMonths(1);
            String monthKey = "main.timereport.select.month." + formatMonth(yearMonth.atDay(1)).toLowerCase() + ".text";

            if (ecId > 0) {
                model.addAttribute("listData", dailyService.buildListView(yearMonth, ecId));
            }
            model.addAttribute("yearMonth", yearMonth);
            // the day the "back to daily view" button jumps to: today while the current month is
            // shown, its first day otherwise - a month the user browsed to has no "current" day
            model.addAttribute("dailyDate",
                yearMonth.equals(YearMonth.from(today)) ? today : yearMonth.atDay(1));
            model.addAttribute("prevMonth", prev.getMonthValue());
            model.addAttribute("prevYear", prev.getYear());
            model.addAttribute("nextMonth", next.getMonthValue());
            model.addAttribute("nextYear", next.getYear());
            model.addAttribute("title", messages.getMessage(monthKey) + " " + targetYear);
        } else {
            YearMonth yearMonth = YearMonth.from(targetDate);
            LocalDate prev = targetDate.minusDays(1);
            LocalDate next = targetDate.plusDays(1);

            // the day shown is the header button's day only where a booking may be created on it;
            // on a released or accepted day it opens the form for today instead (#1164)
            LocalDate bookingDay = targetDate;
            if (ecId > 0) {
                var dailyData = dailyService.buildDailyView(targetDate, ecId);
                if (!dailyData.canCreateTimereport()) {
                    bookingDay = null;
                }
                model.addAttribute("dailyData", dailyData);
                model.addAttribute("weekStripData", dailyData.weekStrip());
                var form = new WorkingdayForm();

                form.setDate(targetDate);
                form.setNotWorked(dailyData.notWorked());
                form.setStartTime(dailyData.startTime());
                form.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", form);
            }
            model.addAttribute("yearMonth", yearMonth);
            model.addAttribute("date", targetDate);
            model.addAttribute("prevDate", prev);
            model.addAttribute("nextDate", next);
            model.addAttribute("title", targetDate.toString());
            // the header button books on the day shown and comes back to it (#1156); the contract is
            // the remembered selection the form falls back to, as for the button in the page itself
            model.addAttribute("newBookingUrl",
                TimereportController.newBookingUrl(bookingDay, null, dailyViewUrl(targetDate, returnUrl)));
            if (ecId > 0) {
                addBookingOffers(model, ecId, targetDate);
            }
        }

        return "dailyreport/daily";
    }

    /**
     * Saves start, break and the not-worked flag of a day. The view sends it through HTMX and gets
     * the bookings fragment back, so the page — and with it the way back to an overview — stays. The
     * plain form post is the fallback without script; its redirect keeps the way back (#760).
     */
    @PostMapping("/workingday")
    public String saveWorkingday(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam(required = false) String returnUrl,
            @ModelAttribute WorkingdayForm form,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        long effEmployeeContractId = effectiveContractId(fEmployeeContractId);
        LocalDate date = form.getDate();
        try {
            var contract = employeecontractService.getEmployeecontractById(effEmployeeContractId);
            Workingday workingday = workingdayService.getWorkingday(effEmployeeContractId, date);
            if (workingday == null) {
                workingday = new Workingday();
                workingday.setEmployeecontract(contract);
                workingday.setRefday(date);
            }
            // Only a changed not-worked flag adds or removes the start/break fields, so only then
            // does the form itself have to be swapped back. Re-rendering it after a plain time
            // change would replace the field the user is working in and take the focus with it,
            // which in turn made the removed field fire its own blur and save a second time (#830).
            boolean wasNotWorked = workingday.getType() == Workingday.WorkingDayType.NOT_WORKED;
            model.addAttribute("renderWorkingdayForm", wasNotWorked != form.isNotWorked());
            if (form.isNotWorked()) {
                workingday.setType(Workingday.WorkingDayType.NOT_WORKED);
                workingday.setStartTime(LocalTime.MIDNIGHT);
                workingday.setBreakLength(Duration.ZERO);
            } else {
                var beginTime = dailyPreferenceService.getForEmployeeContractId(effEmployeeContractId).workDayStart();
                int[] start = parseTime(form.getStartTime(), beginTime.getHour(), beginTime.getMinute());
                // the break is a duration, not a time of day — "30" means half an hour, not 30 o'clock
                int[] brk   = parseBreak(form.getBreakTime());
                workingday.setType(Workingday.WorkingDayType.WORKED);
                workingday.setStartTime(LocalTime.of(start[0], start[1]));
                workingday.setBreakLength(Duration.ofHours(brk[0]).plusMinutes(brk[1]));
            }
            workingdayService.upsertWorkingday(workingday);
            if ("true".equals(request.getHeader("HX-Request"))) {
                var dailyData = dailyService.buildDailyView(date, effEmployeeContractId);
                model.addAttribute("dailyData", dailyData);
                model.addAttribute("weekStripData", dailyData.weekStrip());
                var updatedForm = new WorkingdayForm();

                updatedForm.setDate(date);
                updatedForm.setNotWorked(dailyData.notWorked());
                updatedForm.setStartTime(dailyData.startTime());
                updatedForm.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", updatedForm);
                model.addAttribute("date", date);
                model.addAttribute("selectedContractId", effEmployeeContractId);
                model.addAttribute("isHtmxRequest", true);
                model.addAttribute("isDailyMode", true);
                model.addAttribute("reviewReturnUrl", reviewReturnUrlOf(request));
                addBookingOffers(model, effEmployeeContractId, date);
                return "dailyreport/daily :: dailyBookings";
            }
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.daily.workingday.save.success.text"));
        } catch (ErrorCodeException ex) {
            String errMsg = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
            if ("true".equals(request.getHeader("HX-Request"))) {
                response.setHeader("HX-Trigger", "{\"showError\":\"" + errMsg.replace("\"", "'") + "\"}");
                var dailyData = dailyService.buildDailyView(date, effEmployeeContractId);
                model.addAttribute("dailyData", dailyData);
                model.addAttribute("weekStripData", dailyData.weekStrip());
                var updatedForm = new WorkingdayForm();

                updatedForm.setDate(date);
                updatedForm.setNotWorked(dailyData.notWorked());
                updatedForm.setStartTime(dailyData.startTime());
                updatedForm.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", updatedForm);
                model.addAttribute("date", date);
                model.addAttribute("selectedContractId", effEmployeeContractId);
                model.addAttribute("isHtmxRequest", true);
                model.addAttribute("isDailyMode", true);
                model.addAttribute("reviewReturnUrl", reviewReturnUrlOf(request));
                addBookingOffers(model, effEmployeeContractId, date);
                return "dailyreport/daily :: dailyBookings";
            }
            redirectAttributes.addFlashAttribute("toastError", errMsg);
        }
        return "redirect:" + dailyViewUrl(date, returnUrl);
    }

    /** The daily view of {@code date}, with the way back to an overview if there was one (#760). */
    static String dailyViewUrl(LocalDate date, String returnUrl) {
        var url = "/dailyreport/daily?mode=daily&date=" + date;
        if (ReturnUrls.isReviewPage(returnUrl)) {
            url += "&returnUrl=" + URLEncoder.encode(returnUrl, UTF_8);
        }
        return url;
    }

    /**
     * The way back to an overview for the bookings fragment that HTMX swaps into the page (#760).
     * The request carries no return target of its own, but the page the fragment lands in has one
     * in its address, and the fragment's links to create, edit and delete a booking hand it on —
     * without it they would lead back to the plain day. Anything but an overview is dropped
     * ({@link ReturnUrls#isReviewPage}).
     */
    static String reviewReturnUrlOf(HttpServletRequest request) {
        var currentUrl = request.getHeader("HX-Current-URL");
        if (currentUrl == null) {
            return null;
        }
        try {
            var returnUrl = UriComponentsBuilder.fromUriString(currentUrl).build().getQueryParams().getFirst("returnUrl");
            var decoded = returnUrl != null ? URLDecoder.decode(returnUrl, UTF_8) : null;
            return ReturnUrls.isReviewPage(decoded) ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @PostMapping("/timereport/{id}/update-inline")
    public String updateTimereportInline(
            @PathVariable long id,
            @RequestParam(required = false) String duration,
            @RequestParam(required = false) String taskdescription,
            @RequestParam(required = false) String ticketSuggestionChoice,
            @RequestParam(required = false) List<String> adoptedTicketKeys,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        LocalDate date = today();
        Long ecId = null;
        String htmxError = null;
        InlineTicketSuggestion ticketSuggestion = null;
        try {
            var tr = timereportService.getTimereportById(id);
            date = tr.getReferenceday();
            ecId = tr.getEmployeecontractId();
            long hours = tr.getDuration().toHours();
            long minutes = tr.getDuration().toMinutesPart();
            // tolerant input formats (#830): 1:30, 130, 90m, 2h30, 1,5 — an unparseable value is
            // reported back instead of being discarded silently (#825). The duration parameter is
            // absent when only the comment was edited; then the stored duration stays untouched.
            var parsedMinutes = parseFlexibleMinutes(duration);
            if (parsedMinutes.isPresent()) {
                hours = parsedMinutes.getAsLong() / MINUTES_PER_HOUR;
                minutes = parsedMinutes.getAsLong() % MINUTES_PER_HOUR;
            } else if (duration != null) {
                throw new InvalidDataException(TR_DURATION_INVALID_FORMAT);
            }
            String desc = taskdescription != null ? taskdescription : tr.getTaskdescription();
            // #1326: an edited comment naming tickets that are no reference yet is held, and the row
            // offers them; the answer comes back with ticketSuggestionChoice and saves without asking again
            if (taskdescription != null && (ticketSuggestionChoice == null || ticketSuggestionChoice.isBlank())) {
                ticketSuggestion = ticketSuggestionFor(tr, desc);
            }
            if (ticketSuggestion == null) {
                if ("adopt".equals(ticketSuggestionChoice) && adoptedTicketKeys != null && !adoptedTicketKeys.isEmpty()) {
                    timereportService.updateTimereport(id,
                        ecId, tr.getEmployeeorderId(),
                        date, desc, TimereportController.withAdoptedKeys(tr.getTicketReferences(), adoptedTicketKeys),
                        tr.isTraining(), hours, minutes);
                } else {
                    timereportService.updateTimereport(id,
                        ecId, tr.getEmployeeorderId(),
                        date, desc, tr.isTraining(), hours, minutes);
                }
            }
        } catch (ErrorCodeException ex) {
            htmxError = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
        }
        if ("true".equals(request.getHeader("HX-Request")) && ecId != null) {
            if (htmxError != null) {
                response.setHeader("HX-Trigger", "{\"showError\":\"" + htmxError.replace("\"", "'") + "\"}");
            }
            String currentUrl = request.getHeader("HX-Current-URL");
            boolean isListMode = currentUrl != null && currentUrl.contains("mode=list");
            if (isListMode) {
                var tr = timereportService.getTimereportById(id);
                model.addAttribute("singleTr", tr);
                model.addAttribute("inlineTicketSuggestion", ticketSuggestion);
                model.addAttribute("singleTrEditable", dailyService.isTimereportEditable(tr, ecId));
                model.addAttribute("singleTrDate", date);
                model.addAttribute("singleTrYearMonth", YearMonth.from(date));
                return "dailyreport/daily-list-card :: listTimereportCard";
            }
            var dailyData = dailyService.buildDailyView(date, ecId);
            model.addAttribute("dailyData", dailyData);
            model.addAttribute("weekStripData", dailyData.weekStrip());
            var form = new WorkingdayForm();
            form.setDate(date);
            form.setNotWorked(dailyData.notWorked());
            form.setStartTime(dailyData.startTime());
            form.setBreakTime(dailyData.breakTime());
            model.addAttribute("workingdayForm", form);
            model.addAttribute("date", date);
            model.addAttribute("selectedContractId", ecId);
            model.addAttribute("isHtmxRequest", true);
            model.addAttribute("isDailyMode", true);
            model.addAttribute("reviewReturnUrl", reviewReturnUrlOf(request));
            model.addAttribute("inlineTicketSuggestion", ticketSuggestion);
            addBookingOffers(model, ecId, date);
            return "dailyreport/daily :: dailyBookings";
        }
        return "redirect:" + dailyViewUrl(date, reviewReturnUrlOf(request));
    }

    /**
     * The keys of an edited comment to offer as references before it is saved (#1326), {@code null}
     * where there are none or the suborder has no room left for one.
     */
    private InlineTicketSuggestion ticketSuggestionFor(TimereportDTO tr, String comment) {
        var policy = timereportService.getTicketReferencePolicy(tr.getId());
        var references = tr.getTicketReferences();
        if (policy == null || policy.remaining(references.size()) == 0) {
            return null;
        }
        var keys = TicketReferences.keysNotReferenced(comment, references);
        if (keys.isEmpty()) {
            return null;
        }
        return new InlineTicketSuggestion(tr.getId(), comment, keys, policy.remaining(references.size()),
            ticketReferencePolicyViewHelper.label(policy));
    }

    @PostMapping("/apply-favourite")
    public String applyFavourite(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam Long favoriteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        long ecId = effectiveContractId(fEmployeeContractId);
        try {
            var fav = favoriteService.getOwnFavorite(favoriteId).orElseThrow();
            var beginTime = dailyPreferenceService.getForEmployeeContractId(ecId).workDayStart();
            workingdayService.seedWorkingday(ecId, date, beginTime.getHour(), beginTime.getMinute());
            // the overload with the references (#1029): without it the favourite would hand back
            // everything but the tickets it was made for. More than the suborder allows by now is
            // refused with a message, not cut short (#1326).
            timereportService.createTimereports(ecId, fav.employeeorderId(), date,
                fav.comment(), fav.ticketReferences(), false, fav.hours(), fav.minutes(), 1);
            // only a booking that was created counts as a use (#1414)
            favoriteService.markUsed(favoriteId);
        } catch (ErrorCodeException ex) {
            String err = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
            response.setHeader("HX-Trigger", "{\"showError\":\"" + err.replace("\"", "'") + "\"}");
        }
        var dailyData = dailyService.buildDailyView(date, ecId);
        var wdForm = new WorkingdayForm();
        wdForm.setDate(date);
        wdForm.setNotWorked(dailyData.notWorked());
        wdForm.setStartTime(dailyData.startTime());
        wdForm.setBreakTime(dailyData.breakTime());
        model.addAttribute("dailyData", dailyData);
        model.addAttribute("weekStripData", dailyData.weekStrip());
        model.addAttribute("workingdayForm", wdForm);
        model.addAttribute("date", date);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("isHtmxRequest", true);
        model.addAttribute("isDailyMode", true);
        model.addAttribute("reviewReturnUrl", reviewReturnUrlOf(request));
        addBookingOffers(model, ecId, date);
        model.addAttribute("oobFavourites", true);
        return "dailyreport/daily :: dailyBookings";
    }

    /**
     * Books what an earlier day already carried, at the day on screen (#1017). Everything the
     * booking needs comes with the request rather than being read back from the booking it
     * imitates: the offer is a copy, and a copy the user could have edited before sending is still
     * a copy, not a reference to the original.
     *
     * <p>Follows {@link #applyFavourite} step for step - both create a booking out of one click on
     * the day in view, and doing it twice in two shapes would be two ways for it to go wrong.
     */
    @PostMapping("/apply-previous")
    public String applyPrevious(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam long employeeorderId,
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) List<String> ticketReferences,
            @RequestParam long durationMinutes,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        long ecId = effectiveContractId(fEmployeeContractId);
        try {
            var beginTime = dailyPreferenceService.getForEmployeeContractId(ecId).workDayStart();
            workingdayService.seedWorkingday(ecId, date, beginTime.getHour(), beginTime.getMinute());
            timereportService.createTimereports(ecId, employeeorderId, date,
                comment != null ? comment : "", ticketReferences, false,
                durationMinutes / MINUTES_PER_HOUR, durationMinutes % MINUTES_PER_HOUR, 1);
        } catch (ErrorCodeException ex) {
            String err = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
            response.setHeader("HX-Trigger", "{\"showError\":\"" + err.replace("\"", "'") + "\"}");
        }
        var dailyData = dailyService.buildDailyView(date, ecId);
        var wdForm = new WorkingdayForm();
        wdForm.setDate(date);
        wdForm.setNotWorked(dailyData.notWorked());
        wdForm.setStartTime(dailyData.startTime());
        wdForm.setBreakTime(dailyData.breakTime());
        model.addAttribute("dailyData", dailyData);
        model.addAttribute("weekStripData", dailyData.weekStrip());
        model.addAttribute("workingdayForm", wdForm);
        model.addAttribute("date", date);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("isHtmxRequest", true);
        model.addAttribute("isDailyMode", true);
        model.addAttribute("reviewReturnUrl", reviewReturnUrlOf(request));
        addBookingOffers(model, ecId, date);
        return "dailyreport/daily :: dailyBookings";
    }

    @PostMapping("/delete-favourite")
    public String deleteFavourite(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam Long favoriteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request,
            Model model) {
        long ecId = effectiveContractId(fEmployeeContractId);
        try {
            favoriteService.deleteFavorite(favoriteId);
        } catch (Exception ex) {
            // favourite already gone or access denied — ignore
        }
        var dailyData = dailyService.buildDailyView(date, ecId);
        model.addAttribute("dailyData", dailyData);
        model.addAttribute("weekStripData", dailyData.weekStrip());
        var form = new WorkingdayForm();
        form.setDate(date);
        form.setNotWorked(dailyData.notWorked());
        form.setStartTime(dailyData.startTime());
        form.setBreakTime(dailyData.breakTime());
        model.addAttribute("workingdayForm", form);
        model.addAttribute("date", date);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("isHtmxRequest", true);
        model.addAttribute("isDailyMode", true);
        model.addAttribute("reviewReturnUrl", reviewReturnUrlOf(request));
        addBookingOffers(model, ecId, date);
        model.addAttribute("oobFavourites", true);
        return "dailyreport/daily :: dailyBookings";
    }

    /**
     * The two offers standing above the bookings of a day: the favourites and what the days before
     * it carried (#1017). They are set together because they are rendered together — the fragment
     * {@code dailyBookings} holds both dropdowns, so a caller remembering only one of them would
     * make the other disappear from the page.
     */
    private void addBookingOffers(Model model, long ecId, LocalDate date) {
        // The favourites are the login's own (#1369). On somebody else's day they would offer the
        // login's bookings, not that person's - shown only on the own day (#1414).
        model.addAttribute("favorites", isLoginsOwnContract(ecId) ? buildFavoriteViews(model) : List.of());
        model.addAttribute("previousBookings", buildPreviousBookingViews(ecId, date));
    }

    private List<PreviousBookingView> buildPreviousBookingViews(long ecId, LocalDate date) {
        return timereportService.getPreviousBookings(ecId, date).stream()
            .map(booking -> buildPreviousBookingView(booking, date))
            .filter(Objects::nonNull)
            .toList();
    }

    private PreviousBookingView buildPreviousBookingView(PreviousBooking booking, LocalDate date) {
        var eo = employeeorderService.getEmployeeorderById(booking.employeeorderId());
        // an employee order that has run out in the meantime would be offered and then refused on
        // saving with TR_EMPLOYEE_ORDER_INVALID_REF_DATE - an offer has to be bookable
        if (eo == null || !eo.isValidAt(date)) {
            return null;
        }
        return new PreviousBookingView(booking.employeeorderId(),
            eo.getSuborder().getCompleteOrderSignAndDescription(),
            booking.comment(), booking.ticketReferences(), booking.duration());
    }

    /**
     * The short list of favourites (#1414): the ones used last, as many as the person chose, without
     * groups. All of them are in the dialog "Favoriten", whose link names how many there are.
     */
    private List<FavoriteView> buildFavoriteViews(Model model) {
        var recent = favoriteService.getRecentFavorites();
        model.addAttribute("favoriteCount", recent.total());
        return recent.favorites().stream().map(DailyController::buildFavoriteView).toList();
    }

    private static FavoriteView buildFavoriteView(FavoriteEntry f) {
        return new FavoriteView(f.id(), f.suborderLabel(), f.comment(), f.ticketReferences(), f.duration());
    }

    private boolean isLoginsOwnContract(long ecId) {
        return employeecontractService.getEmployeeIdOfEmployeecontract(ecId)
            .filter(employeeId -> employeeId.equals(authorizedEmployee.getEmployeeId()))
            .isPresent();
    }

    private long effectiveContractId(Long fEmployeeContractId) {
        if (fEmployeeContractId != null && fEmployeeContractId > 0) {
            return fEmployeeContractId;
        }
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
                .map(Employeecontract::getId)
                .orElse(-1L);
    }

    /**
     * Tolerant towards the input formats of the time widget (#830) — "830" or "8.30" reach this
     * point when the field is submitted with Enter, before the client had a chance to normalise on
     * blur. Without that tolerance the value would silently fall back to the default.
     */
    /**
     * The break length. Read as a duration, so two digits are minutes: "30" is half an hour, whereas
     * the same input in a time-of-day field would be an invalid hour and silently fall back to zero
     * (#833). Anything unparseable means no break.
     */
    private static int[] parseBreak(String value) {
        long minutes = parseFlexibleMinutes(value).orElse(0L);
        return new int[]{(int) (minutes / MINUTES_PER_HOUR), (int) (minutes % MINUTES_PER_HOUR)};
    }

    private static int[] parseTime(String hhmm, int defaultHour, int defaultMinute) {
        return parseFlexibleTimeOfDay(hhmm)
            .map(time -> new int[]{time.getHour(), time.getMinute()})
            .orElseGet(() -> new int[]{defaultHour, defaultMinute});
    }

    /**
     * @param returnUrl the overview the day was opened from, if any; the daily view it redirects to
     *                  keeps the way back (#760)
     */
    @PostMapping("/delete-timereport")
    public String deleteTimereport(
            @RequestParam Long timereportId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mode,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String returnUrl,
            RedirectAttributes redirectAttributes) {
        try {
            timereportService.deleteTimereportById(timereportId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.daily.timereport.delete.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        if ("list".equals(mode) && month != null && year != null) {
            return "redirect:/dailyreport/daily?mode=list&fMonth=" + month + "&fYear=" + year;
        }
        return "redirect:" + dailyViewUrl(date, returnUrl);
    }

    @PostMapping("/fill-not-worked")
    public String fillNotWorked(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            RedirectAttributes redirectAttributes) {
        long ecId = effectiveContractId(fEmployeeContractId);
        try {
            matrixService.fillNotWorked(YearMonth.of(year, month), ecId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.matrix.fillnotworked.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        return "redirect:/dailyreport/daily?mode=list&fMonth=" + month + "&fYear=" + year;
    }
}
