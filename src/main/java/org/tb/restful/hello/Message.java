package org.tb.restful.hello;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Message {

    private final String message;
    private final Person person;

}
