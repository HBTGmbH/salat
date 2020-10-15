package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;

@Getter
@Setter
public class ShowStatusReportForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -3493035136151698227L;

    private Long customerOrderId;
    private Boolean showReleased = true;

    @Nonnull
    public Boolean getShowReleased() {
        return this.showReleased == null || this.showReleased;
    }

    @Override
    public void reset(ActionMapping arg0, HttpServletRequest arg1) {
        this.showReleased = false;
    }

}
