package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model to hold response form GDM for UPC search TODO: Unify the model in case of S2S and UPC
 *
 * @author g0k0072
 */
@Getter
@Setter
@NoArgsConstructor
public class GdmDeliveryDocumentResponse {
  private String deliveryStatus;
  private List<DeliveryDocument> deliveryDocuments;
}
