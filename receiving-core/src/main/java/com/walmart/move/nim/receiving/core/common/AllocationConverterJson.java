package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = false)
public class AllocationConverterJson implements AttributeConverter<LabelDataAllocationDTO, String> {

  private Gson gson;

  public AllocationConverterJson() {
    super();
    gson = new Gson();
  }

  @Override
  public String convertToDatabaseColumn(LabelDataAllocationDTO allocations) {
    return gson.toJson(allocations);
  }

  @Override
  public LabelDataAllocationDTO convertToEntityAttribute(String allocations) {
    return gson.fromJson(allocations, LabelDataAllocationDTO.class);
  }
}
