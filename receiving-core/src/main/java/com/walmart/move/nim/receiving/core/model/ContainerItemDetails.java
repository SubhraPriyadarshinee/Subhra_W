package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContainerItemDetails implements Serializable {

  private String trackingId;
  private String locationName;
  private Integer orgUnitId;
  private String containerStatus;
  private List<ContainerDetailDto> childContainerDetails;
  private List<ContainerItemDetail> items;
  private String createUserId;
  private Long containerCreateDate;
  private String deliveryNumber;
}
