package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.model.Distribution;
import java.lang.reflect.Type;
import java.util.List;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

// TODO Needs to be revisit
@Converter(autoApply = false)
public class DistributionConverterJson implements AttributeConverter<List<Distribution>, String> {

  private Gson gson;

  public DistributionConverterJson() {
    super();
    gson = new Gson();
  }

  @Override
  public String convertToDatabaseColumn(List<Distribution> distributions) {
    return gson.toJson(distributions);
  }

  @Override
  public List<Distribution> convertToEntityAttribute(String distribution) {
    Type distributionType =
        new TypeToken<List<Distribution>>() {

          /** */
          private static final long serialVersionUID = 1L;
        }.getType();

    return gson.fromJson(distribution, distributionType);
  }
}
