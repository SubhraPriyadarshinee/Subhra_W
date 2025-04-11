package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Set;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = false)
public class LabelIdentifierConverterJson implements AttributeConverter<Set<String>, String> {
  private Gson gson;

  public LabelIdentifierConverterJson() {
    gson = new Gson();
  }

  @Override
  public String convertToDatabaseColumn(Set<String> attribute) {
    return gson.toJson(attribute);
  }

  @Override
  public Set<String> convertToEntityAttribute(String dbData) {
    Type labelIdentifierType =
        new TypeToken<Set<String>>() {
          private static final long serialVersionUID = 1L;
        }.getType();
    return gson.fromJson(dbData, labelIdentifierType);
  }
}
