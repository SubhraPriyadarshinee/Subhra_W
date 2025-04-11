package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Model to represent Hawkeye Delivery Search Request */
@NoArgsConstructor
@Data
public class DeliverySearchRequest {
  private String doorNumber;
  private String messageId;
  @NotNull private String upc;
  private String locationId;

  @JsonProperty("fromTime")
  private String fromDate;

  @JsonProperty("toTime")
  private String toDate;

  private List<Map<String, String>> scannedDataList;
}
