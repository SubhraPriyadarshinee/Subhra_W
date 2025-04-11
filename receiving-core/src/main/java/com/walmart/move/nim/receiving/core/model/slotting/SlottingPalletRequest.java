package com.walmart.move.nim.receiving.core.model.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingPalletRequest {
  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_MESSAGEID)
  private String messageId;

  private String doorId;
  private String receivingMethod;
  private Integer maxPalletPerSlot;
  private Integer vnpkCount;
  private Integer rotationNbr;
  private String sourceLocation;
  private boolean generateMove;
  private Long deliveryNumber;
  private String crossReference;

  @NotEmpty(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTAINERDETAILS)
  List<SlottingContainerDetails> containerDetails = new ArrayList();
}
