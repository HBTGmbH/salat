package org.tb.user;

import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

@Getter
@Setter
public class ShowSettingsForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 4564667507077065751L;

    private String oldpassword;
    private String newpassword;
    private String confirmpassword;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        oldpassword = "";
        newpassword = "";
        confirmpassword = "";
    }

}
