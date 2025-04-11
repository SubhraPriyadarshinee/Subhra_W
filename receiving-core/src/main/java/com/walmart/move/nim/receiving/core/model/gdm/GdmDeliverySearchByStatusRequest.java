package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.core.model.DeliveryHeaderSearchDetails;
import com.walmart.move.nim.receiving.core.model.PageDetails;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Builder
@Getter
@Setter
public class GdmDeliverySearchByStatusRequest {
  private DeliveryHeaderSearchDetails criteria;
  private PageDetails page;
}
