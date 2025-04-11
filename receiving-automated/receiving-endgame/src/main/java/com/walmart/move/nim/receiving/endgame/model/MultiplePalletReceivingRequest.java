package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class MultiplePalletReceivingRequest {
  @NotEmpty private List<ContainerDTO> containers;
  @NotNull private DeliveryStatus deliveryStatus;
  private ExtraAttributes extraAttributes;
  private Boolean isOverboxingPallet;
}
