package org.tb.common;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Warning implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sort;
    private String text;
    private String link;

}
