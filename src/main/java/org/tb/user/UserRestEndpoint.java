package org.tb.user;


import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/rest/user/")
public class UserRestEndpoint {

  @GetMapping("authenticated")
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public boolean isAuthenticated() {
    return true;
  }
}
