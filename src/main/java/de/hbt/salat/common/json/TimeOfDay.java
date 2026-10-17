package de.hbt.salat.common.json;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * A {@link LocalTime} in JSON as {@code HH:mm}, 00:00 to 23:59 (#1398) — the start of a working day. Read strictly:
 * {@code @JsonFormat(pattern = "HH:mm")} would resolve {@code 24:00} to midnight and take it, where the interfaces
 * answer it with 400.
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
@JacksonAnnotationsInside
@JsonSerialize(using = TimeOfDay.Serializer.class)
@JsonDeserialize(using = TimeOfDay.Deserializer.class)
public @interface TimeOfDay {

    /** {@code HH:mm} without a resolver that carries 24:00 over into the next day. */
    DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    class Serializer extends StdScalarSerializer<LocalTime> {

        public Serializer() {
            super(LocalTime.class);
        }

        @Override
        public void serialize(LocalTime value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(FORMAT.format(value));
        }
    }

    class Deserializer extends StdScalarDeserializer<LocalTime> {

        public Deserializer() {
            super(LocalTime.class);
        }

        @Override
        public LocalTime deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (LocalTime) context.handleUnexpectedToken(LocalTime.class, parser);
            }
            var text = parser.getString();
            try {
                return LocalTime.parse(text.strip(), FORMAT);
            } catch (DateTimeParseException e) {
                return (LocalTime) context.handleWeirdStringValue(LocalTime.class, text,
                    "expected a time of day HH:mm, 00:00 to 23:59");
            }
        }
    }
}
