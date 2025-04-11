package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA Converter class to convert a json column back and fourth
 *
 * @author g0k0072
 */
@Converter(autoApply = false)
public class JpaConverterJson implements AttributeConverter<Object, String> {
  private static final Logger log = LoggerFactory.getLogger(JpaConverterJson.class);
  protected static final Gson gson = new GsonBuilder().create();

  /**
   * @param data Object that need to be persisted into db column
   * @return json string else null in case of exceptiom
   */
  @Override
  public String convertToDatabaseColumn(Object data) {
    try {
      return gson.toJson(data);
    } catch (Exception exception) {
      log.error("Error converting meta to database column");
      return null;
    }
  }

  /**
   * @param dbData convert the stored data in db column to Object
   * @return Object form of json column
   */
  @Override
  public Object convertToEntityAttribute(String dbData) {
    try {
      return gson.fromJson(dbData, Object.class);
    } catch (JsonSyntaxException jsonSyntaxException) {
      log.error("Unexpected IOEx decoding json from database: {} ", dbData);
      return null;
    }
  }
}
