package de.hbt.salat.common.json;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.ser.std.StdScalarSerializer;
import de.hbt.salat.common.util.DurationUtils;

/**
 * A {@link Duration} in JSON as {@code HH:mm} (#1398), the one format every interface uses for a duration:
 * hours beyond a day included ({@code 25:30}), no sign. Jackson's own format would be ISO-8601
 * ({@code PT4H30M}), and a {@code LocalTime} would end at 23:59 and be a time of day by its type.
 *
 * <p>A value that {@link DurationUtils#parseHoursMinutes} does not read — minutes of 60 or more, a sign,
 * another format — fails the request with 400 before it reaches the application.
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
@JacksonAnnotationsInside
@JsonSerialize(using = HoursMinutes.Serializer.class)
@JsonDeserialize(using = HoursMinutes.Deserializer.class)
public @interface HoursMinutes {

    /** The pattern documented for the field in the OpenAPI schema. */
    String PATTERN = "^\\d{2,}:[0-5]\\d$";

    /** The pattern of {@link Signed}. */
    String SIGNED_PATTERN = "^-?\\d{2,}:[0-5]\\d$";

    /**
     * A balance, which may be negative, as {@code HH:mm} with a leading minus ({@code -12:30}). Only handed out:
     * no interface takes a balance.
     */
    @Retention(RUNTIME)
    @Target({FIELD, METHOD, PARAMETER})
    @JacksonAnnotationsInside
    @JsonSerialize(using = SignedSerializer.class)
    @interface Signed {
    }

    class SignedSerializer extends StdScalarSerializer<Duration> {

        public SignedSerializer() {
            super(Duration.class);
        }

        @Override
        public void serialize(Duration value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(DurationUtils.formatSignedHoursMinutes(value));
        }
    }

    class Serializer extends StdScalarSerializer<Duration> {

        public Serializer() {
            super(Duration.class);
        }

        @Override
        public void serialize(Duration value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(DurationUtils.formatHoursMinutes(value));
        }
    }

    class Deserializer extends StdScalarDeserializer<Duration> {

        public Deserializer() {
            super(Duration.class);
        }

        @Override
        public Duration deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (Duration) context.handleUnexpectedToken(Duration.class, parser);
            }
            var text = parser.getString();
            return DurationUtils.parseHoursMinutes(text).orElseGet(() -> (Duration) context.handleWeirdStringValue(
                Duration.class, text, "expected a duration HH:mm, minutes 00-59, no sign"));
        }
    }
}
