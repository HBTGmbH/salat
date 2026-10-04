package de.hbt.salat.dailyreport.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import de.hbt.salat.dailyreport.preferences.DurationInputMode;

@Data
public class TimereportForm {

    private Long id;

    /**
     * The contract a new booking is for, when the form was opened for one (#760); {@code null}
     * otherwise, and then the remembered selection applies. An edited booking ignores it: it stays
     * with its own person (#1128).
     */
    private Long employeecontractId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate referenceday;

    private Long orderId;
    private Long suborderId;

    /**
     * Key of a {@link DurationInputMode} — controlled by the JS toggle buttons. Stays a String
     * because that is what the hidden form field submits; the enum keys are the wire format.
     * Empty on a create form while the user has no preferred entry mode yet (#844); the form then
     * decides on load.
     */
    private String durationMode = DurationInputMode.DURATION.getKey();

    /** HH:MM (e.g. "01:30") — only used in {@link DurationInputMode#DURATION} mode */
    private String durationTime = "";

    /** HH:MM — only used in {@link DurationInputMode#BEGIN_END} mode */
    private String beginTime;
    /** HH:MM — only used in {@link DurationInputMode#BEGIN_END} mode */
    private String endTime;

    private String comment = "";

    /**
     * The ticket references of the booking (#982, #1326), as many as its suborder allows. Free text
     * on purpose: the suggestions are a convenience, not a constraint, and a ticket that was never
     * replicated must still be bookable.
     */
    private List<String> ticketReferences = new ArrayList<>();

    /** Blank entries drop out: an empty field of the select is no reference. */
    public void setTicketReferences(List<String> ticketReferences) {
        this.ticketReferences = ticketReferences == null ? new ArrayList<>()
            : ticketReferences.stream().filter(reference -> reference != null && !reference.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * The answer to the keys proposed from the comment when saving was held (#1326): {@code adopt} adds
     * {@link #adoptedTicketKeys}, {@code skip} saves without them. Set by the two buttons of the
     * proposal; empty on a first submit, which is the one that may be held.
     */
    private String ticketSuggestionChoice;

    /** The proposed keys that were ticked. */
    private List<String> adoptedTicketKeys = new ArrayList<>();

    private boolean training;

    /** 1 = no repeat; > 1 = create one timereport per working day, skipping weekends/holidays */
    private int numberOfSerialDays = 1;

    private boolean saveAsFavorite;
}
