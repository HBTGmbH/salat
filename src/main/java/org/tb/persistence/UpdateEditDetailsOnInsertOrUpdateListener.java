package org.tb.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.event.PreInsertEvent;
import org.hibernate.event.PreInsertEventListener;
import org.hibernate.event.PreUpdateEvent;
import org.hibernate.event.PreUpdateEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.EditDetails;
import org.tb.util.DateUtils;

@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired})
public class UpdateEditDetailsOnInsertOrUpdateListener implements PreInsertEventListener, PreUpdateEventListener  {

  private final AuthorizedUser authorizedUser;

  @Override
  public boolean onPreInsert(PreInsertEvent event) {
    if(event.getEntity() instanceof EditDetails) {
      EditDetails editDetails = (EditDetails) event.getEntity();
      editDetails.setCreated(DateUtils.now());
      editDetails.setLastupdate(DateUtils.now());
      editDetails.setUpdatecounter(0);
      editDetails.setCreatedby(authorizedUser.getSign());
    }
    return false;
  }

  @Override
  public boolean onPreUpdate(PreUpdateEvent event) {
    if(event.getEntity() instanceof EditDetails) {
      EditDetails editDetails = (EditDetails) event.getEntity();
      editDetails.setLastupdate(DateUtils.now());
      editDetails.setLastupdatedby(authorizedUser.getSign());
      Integer updatecounter = Optional.ofNullable(editDetails.getUpdatecounter()).orElse(0);
      editDetails.setUpdatecounter(updatecounter + 1);
    }
    return false;
  }
}
