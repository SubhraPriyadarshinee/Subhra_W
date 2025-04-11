package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {
  private String documentId;
  private String shipmentNumber;
  private String documentType;
  private Integer documentRevision;
  private Date documentIngestTime;
  private String shipmentDate;
  private String bolNumber;
  private String loadNumber;
  private Source source;
  private Destination destination;
  private ShipmentDetails shipmentDetail;
  private Integer totalPacks = 0;
  private ShipmentAdditionalInfo additionalInfo;
}
