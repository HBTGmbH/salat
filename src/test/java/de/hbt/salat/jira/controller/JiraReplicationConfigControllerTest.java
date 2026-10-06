package de.hbt.salat.jira.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfigInfo;

/**
 * The replication maintenance page (#984). What is worth pinning down here is the boundary: the
 * credentials of a foreign system are behind these URLs, so "management only" has to hold for every
 * one of them and not just for the ones the menu leads to.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationConfigControllerTest {

  @Test
  void the_whole_controller_is_management_only() {
    // Not only the writes: knowing the URL must not get a backoffice or a people lead to the list.
    var authorized = JiraReplicationConfigController.class.getAnnotation(Authorized.class);
    assertThat(authorized).isNotNull();
    assertThat(authorized.requiresManager()).isTrue();
  }

  @Test
  void every_write_carries_its_own_guard() {
    var writes = Arrays.stream(JiraReplicationConfigController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .toList();

    assertThat(writes).isNotEmpty();
    assertThat(writes).allSatisfy(method -> assertThat(guardOf(method))
        .as("@Authorized on %s", method.getName())
        .isNotNull()
        .satisfies(guard -> assertThat(guard.requiresManager()).isTrue()));
  }

  @Test
  void an_edit_form_starts_without_a_password() {
    // The form is filled from the info record, which has none — so an edit cannot show the stored
    // token, and the empty field it starts with is what the service reads as "keep it".
    var form = JiraReplicationConfigForm.of(info(1L, null));

    assertThat(form.getPassword()).isNull();
    assertThat(form.getUsername()).isEqualTo("jira-user");
    assertThat(form.isNew()).isFalse();
  }

  @Test
  void an_order_wide_scope_opens_the_form_with_no_suborder_chosen() {
    var form = JiraReplicationConfigForm.of(info(1L, null));

    assertThat(form.getCustomerorderId()).isEqualTo(1L);
    assertThat(form.getSuborderId()).isNull();
  }

  @Test
  void a_suborder_scope_opens_the_form_on_its_order_and_suborder() {
    // by id (#1322): whatever the two are called by now, the form opens on them
    var form = JiraReplicationConfigForm.of(info(1L, 12L));

    assertThat(form.getCustomerorderId()).isEqualTo(1L);
    assertThat(form.getSuborderId()).isEqualTo(12L);
  }

  @Test
  void a_new_replication_is_offered_switched_on_and_as_server() {
    var form = new JiraReplicationConfigForm();

    assertThat(form.isNew()).isTrue();
    assertThat(form.isEnabled()).isTrue();
    assertThat(form.getApiFlavor()).isEqualTo(JiraApiFlavor.SERVER);
    assertThat(form.getPassword()).isNull();
  }

  private static JiraReplicationConfigInfo info(long customerorderId, Long suborderId) {
    return new JiraReplicationConfigInfo(7L, "Alpha", customerorderId, suborderId, "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "project = ALPHA", null, null, null, 100, true, false, null, false, null);
  }

  private static Authorized guardOf(Method method) {
    return method.getAnnotation(Authorized.class);
  }
}
