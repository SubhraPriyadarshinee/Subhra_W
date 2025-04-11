package com.walmart.move.nim.receiving.core.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class ChannelMethodListConverterJson implements AttributeConverter<List<String>, String> {

  @Override
  public String convertToDatabaseColumn(List<String> strings) {
    return String.join(",", strings);
  }

  @Override
  public List<String> convertToEntityAttribute(String s) {
    return new ArrayList<>(Arrays.asList(s.split(",")));
  }
}
