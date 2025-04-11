package com.walmart.move.nim.receiving.core.model.symbotic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.*;
import org.springframework.http.HttpHeaders;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdcVerificationMessage extends MessageData {
  private String locationId;

  @SerializedName("groupNbr")
  @JsonProperty("groupNbr")
  private String deliveryNumber;

  private String lpn;
  private String messageType;
  private String eventTs;
  private String inboundTagId;
  private boolean palletReceivedStatus;
  private List<String> backoutLpnList;
  private HttpHeaders httpHeaders;
}
