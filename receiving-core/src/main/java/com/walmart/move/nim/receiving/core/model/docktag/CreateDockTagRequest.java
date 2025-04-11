package com.walmart.move.nim.receiving.core.model.docktag;

import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDockTagRequest {

  @NotNull(message = "deliveryNumber cannot be empty")
  private Long deliveryNumber;

  @NotNull(message = "doorNumber cannot be empty")
  private String doorNumber;

  private String mappedPbylArea;

  private Integer count;
  private String carrier;
  private String trailerNumber;
  private DockTagType dockTagType;
  private String deliveryTypeCode;
  private String freightType;
}
