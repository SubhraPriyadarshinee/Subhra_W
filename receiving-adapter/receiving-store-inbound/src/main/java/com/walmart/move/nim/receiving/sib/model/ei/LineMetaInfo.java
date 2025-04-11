package com.walmart.move.nim.receiving.sib.model.ei;

import java.util.Date;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class LineMetaInfo {
  private String bannerDescription;
  private String timezone;
  private String wareHouseAreaCode;
  private Long deliveryNumber;
  private String palletType;
  private String containerType;
  private Date scheduleTs;
  private Date arriveTs;
  private Date documentIngestTime;
}
