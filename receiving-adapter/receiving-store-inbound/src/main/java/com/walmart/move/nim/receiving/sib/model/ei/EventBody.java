package com.walmart.move.nim.receiving.sib.model.ei;

import java.util.List;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class EventBody {

  private SourceNode sourceNode;
  private List<LineItem> lineInfo;
  private DocumentInfo documents;
  private String userId;
  private List<ReasonDetail> reasonDetails;
}
