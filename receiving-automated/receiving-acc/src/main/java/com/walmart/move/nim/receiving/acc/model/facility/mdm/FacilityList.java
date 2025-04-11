package com.walmart.move.nim.receiving.acc.model.facility.mdm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityList {

  @JsonProperty(value = "foundLocation")
  private List<BusinessUnitDetail> foundBusinessUnits = new ArrayList<>();

  @JsonProperty(value = "notFoundLocation")
  private List<String> notFoundBusinessUnits = new ArrayList<>();
}
