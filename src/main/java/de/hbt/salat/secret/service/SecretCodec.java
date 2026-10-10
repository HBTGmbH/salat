package de.hbt.salat.secret.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import de.hbt.salat.secret.domain.SecretType;
import de.hbt.salat.secret.domain.SecretValue;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.UsernamePassword;

/**
 * The content of a secret as the bytes that are encrypted (#1432): a format version, then the fields
 * of the type in a fixed order, each as its length and UTF-8 bytes, {@code -1} for {@code null}.
 * Which type the bytes hold is not part of them; it stands in {@code secret.type}.
 */
final class SecretCodec {

  private static final byte VERSION = 1;

  private SecretCodec() {
  }

  static byte[] encode(SecretValue value) {
    return switch (value) {
      case UsernamePassword usernamePassword -> write(usernamePassword.username(), usernamePassword.password());
      case Token token -> write(token.token());
    };
  }

  static SecretValue decode(SecretType type, byte[] bytes) {
    var fields = read(bytes);
    return switch (type) {
      case USERNAME_PASSWORD -> new UsernamePassword(field(fields, 0), field(fields, 1));
      case TOKEN -> new Token(field(fields, 0));
    };
  }

  private static byte[] write(String... fields) {
    var bytes = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(bytes)) {
      out.writeByte(VERSION);
      out.writeInt(fields.length);
      for (var field : fields) {
        if (field == null) {
          out.writeInt(-1);
        } else {
          var utf8 = field.getBytes(StandardCharsets.UTF_8);
          out.writeInt(utf8.length);
          out.write(utf8);
        }
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return bytes.toByteArray();
  }

  private static List<String> read(byte[] bytes) {
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      var version = in.readByte();
      if (version != VERSION) {
        throw new IllegalStateException("Unknown format version " + version + " of a secret");
      }
      var count = in.readInt();
      var fields = new ArrayList<String>(count);
      for (int i = 0; i < count; i++) {
        var length = in.readInt();
        fields.add(length < 0 ? null : new String(in.readNBytes(length), StandardCharsets.UTF_8));
      }
      return fields;
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static String field(List<String> fields, int index) {
    return index < fields.size() ? fields.get(index) : null;
  }
}
