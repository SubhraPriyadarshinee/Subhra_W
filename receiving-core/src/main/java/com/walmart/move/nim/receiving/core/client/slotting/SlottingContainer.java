package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SlottingContainer {

  @NotEmpty(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTENTS)
  private List<SlottingContainerContents> contents = new ArrayList<>();

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTAINERSTATUS)
  private String containerStatus;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTAINERTRACKINGID)
  private String containerTrackingId;
}
