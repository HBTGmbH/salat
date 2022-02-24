package org.tb.order.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SuborderData {

    private final long id;
    private final String label;
    private final boolean commentRequired;

}
