package org.tb.auth.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizationRuleData;
import org.tb.auth.domain.AuthorizationRuleInfo;

/**
 * The form behind the authorization rule editor (#1074).
 *
 * <p>Grantees, objects and access levels are lists because the columns are: 11 of the 16 rules that existed when this
 * was built name more than one grantee. An editor that took a single value could not have opened them.
 */
@Getter
@Setter
public class AuthorizationRuleForm {

  private Long id;
  private String category;
  private List<String> granteeIds = new ArrayList<>();
  private List<String> objectIds = new ArrayList<>();
  private List<AccessLevel> accessLevels = new ArrayList<>();

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate validFrom;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate validUntil;

  public boolean isNew() {
    return id == null;
  }

  public AuthorizationRuleData toData() {
    return new AuthorizationRuleData(category, granteeIds, objectIds, accessLevels, validFrom, validUntil);
  }

  public static AuthorizationRuleForm of(AuthorizationRuleInfo info) {
    var form = new AuthorizationRuleForm();
    form.setId(info.id());
    form.setCategory(info.category());
    form.setGranteeIds(new ArrayList<>(info.granteeIds()));
    form.setObjectIds(new ArrayList<>(info.objectIds()));
    form.setAccessLevels(new ArrayList<>(info.accessLevels()));
    form.setValidFrom(info.validFrom());
    form.setValidUntil(info.validUntil());
    return form;
  }

}
