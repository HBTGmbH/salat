package de.hbt.salat.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.LOGIN;

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
import de.hbt.salat.auth.domain.AuthorizationRuleInfo;
import de.hbt.salat.auth.domain.AuthorizationRuleValue;
import de.hbt.salat.auth.service.AuthorizationRuleService;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;

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

    private static final AuthorizationRuleValue OFFERED = new AuthorizationRuleValue("11", OFFERED_LOGIN, null, false);
    private static final AuthorizationRuleValue HIDDEN = new AuthorizationRuleValue("12", HIDDEN_LOGIN, null, false);

    @BeforeEach
    void setUp() {
        // the hidden person appears in neither list the modules offer
        when(authorizationRuleService.getGranteeCandidates(List.of())).thenReturn(List.of(OFFERED));
        when(authorizationRuleService.getObjects(null, List.of())).thenReturn(List.of());
        when(authorizationRuleService.getCategories(null)).thenReturn(List.of("EMPLOYEE"));
        when(authorizationRuleService.getCategories("EMPLOYEE")).thenReturn(List.of("EMPLOYEE"));
    }

    @SuppressWarnings("unchecked")
    private static List<AuthorizationRuleValue> grantees(ExtendedModelMap model) {
        return (List<AuthorizationRuleValue>) model.getAttribute("granteeCandidates");
    }

    @SuppressWarnings("unchecked")
    private static List<AuthorizationRuleValue> objects(ExtendedModelMap model) {
        return (List<AuthorizationRuleValue>) model.getAttribute("objectCandidates");
    }

    @Test
    void aNewRuleOffersOnlyWhatIsNotHidden() {
        var model = new ExtendedModelMap();

        controller.createForm(model);

        assertThat(grantees(model)).containsExactly(OFFERED);
        assertThat(objects(model)).isEmpty();
    }

    /**
     * What the rule carries goes to the service as the values to keep: the service adds back what the modules no
     * longer offer, named by the record it names today (#1204).
     */
    @Test
    void editingKeepsWhatTheRuleAlreadyNamesEvenWhenItIsHidden() {
        when(authorizationRuleService.getById(1L)).thenReturn(new AuthorizationRuleInfo(
            1L, "Vertretung", "EMPLOYEE", "main.auth.rule.category.employee",
            List.of("12"), List.of("12"), List.of(HIDDEN), List.of(HIDDEN), List.of(LOGIN), null, null));
        when(authorizationRuleService.getGranteeCandidates(List.of("12"))).thenReturn(List.of(OFFERED, HIDDEN));
        when(authorizationRuleService.getObjects("EMPLOYEE", List.of("12"))).thenReturn(List.of(OFFERED, HIDDEN));
        var model = new ExtendedModelMap();

        controller.editForm(1L, model);

        assertThat(grantees(model))
            .as("a grantee that is hidden must stay selectable while editing the rule that names them")
            .contains(HIDDEN, OFFERED);
        assertThat(objects(model))
            .as("the same for the object of the rule")
            .contains(HIDDEN, OFFERED);
    }

}
