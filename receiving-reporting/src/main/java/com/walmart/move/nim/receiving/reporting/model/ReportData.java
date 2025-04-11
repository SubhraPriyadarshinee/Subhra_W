package com.walmart.move.nim.receiving.reporting.model;

import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** @author sks0013 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ReportData {

  private List<Pair<String, Object>> statisticsData;
  private List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponses;
  private List<UserCaseChannelTypeResponse> caseChannelTypeResponses;
}
