package org.tb.common.viewhelper.fragment;

import org.springframework.stereotype.Component;

@Component("dangerZoneParams")
public class DangerZoneParamsFactory {

    public DangerZoneParams card(String cardTitle, String description, String buttonLabel, String modalId) {
        return DangerZoneParams.builder()
            .cardTitle(cardTitle).description(description)
            .buttonLabel(buttonLabel).modalId(modalId).build();
    }

    public DangerZoneModalParams modal(String modalId, String modalTitle, String warningMessage, String submitLabel) {
        return DangerZoneModalParams.builder()
            .modalId(modalId).modalTitle(modalTitle)
            .warningMessage(warningMessage).submitLabel(submitLabel).build();
    }
}
