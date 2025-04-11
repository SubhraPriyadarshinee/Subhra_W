/** */
package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import java.lang.reflect.Type;
import java.util.List;

/** @author pcr000m */
public class ChildContainerConverterJson {

  private static Gson gson = new Gson();

  private ChildContainerConverterJson() {
    // Object creation not required
  }

  public static String convertToDatabaseColumn(List<ContainerDetails> childContainer) {
    return gson.toJson(childContainer);
  }

  public static List<ContainerDetails> convertToEntityAttribute(String childContainer) {
    Type childContainerType =
        new TypeToken<List<ContainerDetails>>() {

          /** */
          private static final long serialVersionUID = 1L;
        }.getType();

    return gson.fromJson(childContainer, childContainerType);
  }
}
