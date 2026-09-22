package org.tb.jira.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.tb.auth.domain.Authorized;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraReplicationConfigInfo;

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

    var preAuthorize = JiraReplicationConfigController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo("hasRole('MANAGER')");
  }

  @Test
  void every_write_carries_its_own_guard() {
    var writes = Arrays.stream(JiraReplicationConfigController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .toList();

    assertThat(writes).isNotEmpty();
    assertThat(writes).allSatisfy(method -> assertThat(guardOf(method))
        .as("@PreAuthorize on %s", method.getName())
        .isEqualTo("hasRole('MANAGER')"));
  }

  @Test
  void an_edit_form_starts_without_a_password() {
    // The form is filled from the info record, which has none — so an edit cannot show the stored
    // token, and the empty field it starts with is what the service reads as "keep it".
    var form = JiraReplicationConfigForm.of(info("ALPHA"), "ALPHA");

    assertThat(form.getPassword()).isNull();
    assertThat(form.getUsername()).isEqualTo("jira-user");
    assertThat(form.isNew()).isFalse();
  }

  @Test
  void an_order_wide_scope_opens_the_form_with_no_suborder_chosen() {
    var form = JiraReplicationConfigForm.of(info("ALPHA"), "ALPHA");

    assertThat(form.getCustomerorderSign()).isEqualTo("ALPHA");
    assertThat(form.getSuborderSign()).isNull();
    assertThat(form.getScopeSign()).isEqualTo("ALPHA");
  }

  @Test
  void a_suborder_scope_keeps_the_full_path_and_names_its_order() {
    // the select offers the fully qualified sign as its option value, and the order beside it is
    // what the list of suborders is loaded for
    var form = JiraReplicationConfigForm.of(info("ALPHA/A/01"), "ALPHA");

    assertThat(form.getCustomerorderSign()).isEqualTo("ALPHA");
    assertThat(form.getSuborderSign()).isEqualTo("ALPHA/A/01");
    assertThat(form.getScopeSign()).isEqualTo("ALPHA/A/01");
  }

  @Test
  void an_order_sign_with_a_slash_in_it_stays_order_wide() {
    // "0283/03.20" is one order, not an order plus a suborder — splitting the scope at its first
    // slash would open the form on the order "0283" and move the replication there on the next save
    var form = JiraReplicationConfigForm.of(info("0283/03.20"), "0283/03.20");

    assertThat(form.getCustomerorderSign()).isEqualTo("0283/03.20");
    assertThat(form.getSuborderSign()).isNull();
    assertThat(form.getScopeSign()).isEqualTo("0283/03.20");
  }

  @Test
  void a_suborder_below_an_order_whose_sign_has_a_slash_keeps_both_apart() {
    var form = JiraReplicationConfigForm.of(info("0283/03.20/F&E/01"), "0283/03.20");

    assertThat(form.getCustomerorderSign()).isEqualTo("0283/03.20");
    assertThat(form.getSuborderSign()).isEqualTo("0283/03.20/F&E/01");
  }

  @Test
  void clearing_the_suborder_puts_the_replication_back_on_the_whole_order() {
    var form = JiraReplicationConfigForm.of(info("ALPHA/A/01"), "ALPHA");

    form.setSuborderSign("");

    assertThat(form.getScopeSign()).isEqualTo("ALPHA");
  }

  @Test
  void a_new_replication_is_offered_switched_on_and_as_server() {
    var form = new JiraReplicationConfigForm();

    assertThat(form.isNew()).isTrue();
    assertThat(form.isEnabled()).isTrue();
    assertThat(form.getApiFlavor()).isEqualTo(JiraApiFlavor.SERVER);
    assertThat(form.getPassword()).isNull();
  }

  private static JiraReplicationConfigInfo info(String scopeSign) {
    return new JiraReplicationConfigInfo(7L, "Alpha", scopeSign, "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "project = ALPHA", null, null, null, 100, true, false, null, null);
  }

  private static String guardOf(Method method) {
    var preAuthorize = method.getAnnotation(PreAuthorize.class);
    return preAuthorize == null ? null : preAuthorize.value();
  }
}
