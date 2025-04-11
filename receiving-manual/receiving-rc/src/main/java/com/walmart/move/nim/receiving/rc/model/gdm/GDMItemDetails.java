package com.walmart.move.nim.receiving.rc.model.gdm;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDMItemDetails {
  private Long number;
  private String consumableGTIN;
  private String orderableGTIN;
  private List<String> descriptions;
  private AdditionalInformation additionalInformation;
  private String itemId;
  private String dotIdNbr;
  private Long supplierNumber;
}
