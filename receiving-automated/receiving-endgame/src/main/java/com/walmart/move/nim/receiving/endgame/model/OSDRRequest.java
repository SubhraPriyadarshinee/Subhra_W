package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import javax.validation.constraints.Size;
import lombok.*;
import org.springframework.util.CollectionUtils;

@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OSDRRequest {

  @Size(min = 0, max = 20, message = "Delivery Numbers size can not be more than 20")
  private List<String> deliveryNos;

  @Size(min = 0, max = 100, message = "POs size can not be more than 100")
  private List<String> poNos;

  public boolean isValid() {
    boolean isValid = true;
    if (CollectionUtils.isEmpty(this.getDeliveryNos())
        && CollectionUtils.isEmpty(this.getPoNos())) {
      isValid = false;
    } else if (!CollectionUtils.isEmpty(this.getDeliveryNos())
        && !CollectionUtils.isEmpty(this.getPoNos())) {
      isValid = false;
    }
    return isValid;
  }
}
