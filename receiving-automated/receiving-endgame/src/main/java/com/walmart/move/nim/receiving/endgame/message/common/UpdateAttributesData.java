package com.walmart.move.nim.receiving.endgame.message.common;

import com.walmart.move.nim.receiving.endgame.model.ItemAttributes;
import com.walmart.move.nim.receiving.endgame.model.SearchCriteria;
import com.walmart.move.nim.receiving.endgame.model.UpdateAttributes;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UpdateAttributesData extends MessageData {
  private SearchCriteria searchCriteria;
  private UpdateAttributes updateAttributes;
  private ItemAttributes itemAttributes;
}
