package org.tb.etl.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Converter(autoApply = true)
public class SqlStatementsConverter implements AttributeConverter<SqlStatements, String> {

  @Override
  public String convertToDatabaseColumn(SqlStatements attribute) {
    if (attribute == null || attribute.getStatements() == null || attribute.getStatements().isEmpty()) {
      return null;
    }
    return String.join(";", attribute.getStatements());
  }

  @Override
  public SqlStatements convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty()) {
      return new SqlStatements(new ArrayList<>());
    }
    return new SqlStatements(Arrays.asList(dbData.split(";")).stream().map(String::trim).toList());
  }

}
