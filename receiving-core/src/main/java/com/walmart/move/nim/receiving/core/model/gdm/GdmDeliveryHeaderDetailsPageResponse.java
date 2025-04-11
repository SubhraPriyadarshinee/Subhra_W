package com.walmart.move.nim.receiving.core.model.gdm;

import java.util.List;
import lombok.Data;

@Data
public class GdmDeliveryHeaderDetailsPageResponse {

  private List<GdmDeliveryHeaderDetailsResponse> data;
  private GdmDeliveryHeaderPageDetails page;
}
