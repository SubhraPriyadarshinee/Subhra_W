package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.model.Labels;
import java.lang.reflect.Type;

public class LabelConverterJson {

  private static Gson gson = new Gson();

  private LabelConverterJson() {
    // Object creation not required
  }

  public static String convertToDatabaseColumn(Labels labels) {
    return gson.toJson(labels);
  }

  public static Labels convertToEntityAttribute(String labels) {
    Type labelsType =
        new TypeToken<Labels>() {

          /** */
          private static final long serialVersionUID = 1L;
        }.getType();

    return gson.fromJson(labels, labelsType);
  }
}
