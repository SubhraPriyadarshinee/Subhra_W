package com.walmart.move.nim.receiving.core.model.docktag;

import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DockTagResponse {

  private Long deliveryNumber;
  private List<String> dockTags;
  private PrintLabelData printData;
}
