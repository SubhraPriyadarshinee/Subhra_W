package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import java.util.List;
import lombok.*;

/** Model to represent Hawkeye Delivery Search Request */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySearchResponse {
  private List<DeliveryDocument> deliveryDocuments;
}
