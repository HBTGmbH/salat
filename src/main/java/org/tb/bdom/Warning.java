package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Warning implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sort;
    private String text;
    private String link;

}
