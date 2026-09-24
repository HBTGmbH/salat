package org.tb.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.LOGIN;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationRuleInfo;
import org.tb.auth.service.AuthorizationRuleService;
import org.tb.common.viewhelper.ErrorCodeViewHelper;

/**
 * Hiding somebody takes them out of the select boxes — but never out of a rule that already names them (#1074). Were
 * that not so, opening such a rule would drop the value and save back whatever the browser preselected instead.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class AuthorizationRuleControllerTest {

    private static final String HIDDEN_LOGIN = "l.gegangen";
    private static final String OFFERED_LOGIN = "l.muster";

    @Mock
    private AuthorizationRuleService authorizationRuleService;

    @Mock
    private ErrorCodeViewHelper errorCodeViewHelper;

    @Mock
    private MessageSourceAccessor messages;

    @InjectMocks
    private AuthorizationRuleController controller;

    @BeforeEach
    void setUp() {
        // the hidden person appears in neither list the modules offer
        when(authorizationRuleService.getGranteeCandidates()).thenReturn(List.of(OFFERED_LOGIN));
        when(authorizationRuleService.getObjects("EMPLOYEE"))
            .thenReturn(List.of(new AuthorizationObject(OFFERED_LOGIN, OFFERED_LOGIN)));
        when(authorizationRuleService.getCategories(null)).thenReturn(List.of("EMPLOYEE"));
        when(authorizationRuleService.getCategories("EMPLOYEE")).thenReturn(List.of("EMPLOYEE"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> grantees(ExtendedModelMap model) {
        return (List<String>) model.getAttribute("granteeCandidates");
    }

    @SuppressWarnings("unchecked")
    private static List<AuthorizationObject> objects(ExtendedModelMap model) {
        return (List<AuthorizationObject>) model.getAttribute("objectCandidates");
    }

    @Test
    void aNewRuleOffersOnlyWhatIsNotHidden() {
        var model = new ExtendedModelMap();

        controller.createForm(model);

        assertThat(grantees(model)).containsExactly(OFFERED_LOGIN);
        assertThat(objects(model)).isEmpty();
    }

    @Test
    void editingKeepsWhatTheRuleAlreadyNamesEvenWhenItIsHidden() {
        when(authorizationRuleService.getById(1L)).thenReturn(new AuthorizationRuleInfo(
            1L, "EMPLOYEE", "main.auth.rule.category.employee",
            List.of(HIDDEN_LOGIN), List.of(HIDDEN_LOGIN), List.of(LOGIN), null, null));
        var model = new ExtendedModelMap();

        controller.editForm(1L, model);

        assertThat(grantees(model))
            .as("a grantee that is hidden must stay selectable while editing the rule that names them")
            .contains(HIDDEN_LOGIN, OFFERED_LOGIN);
        assertThat(objects(model))
            .as("the same for the object of the rule")
            .contains(new AuthorizationObject(HIDDEN_LOGIN, HIDDEN_LOGIN),
                new AuthorizationObject(OFFERED_LOGIN, OFFERED_LOGIN));
    }

}
