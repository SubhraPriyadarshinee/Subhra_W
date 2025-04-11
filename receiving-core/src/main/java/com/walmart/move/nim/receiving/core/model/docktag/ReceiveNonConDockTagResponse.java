package com.walmart.move.nim.receiving.core.model.docktag;

import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveNonConDockTagResponse {

  private DeliveryDetails delivery;

  private LocationInfo locationInfo;
}
