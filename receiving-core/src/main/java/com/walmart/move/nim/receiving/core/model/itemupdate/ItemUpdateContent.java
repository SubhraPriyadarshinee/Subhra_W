package com.walmart.move.nim.receiving.core.model.itemupdate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemUpdateContent {
  private String lastUpdateTs;
  private String lastUpdateUserId;
  private String limitedQuantityLTD;
  private String lithiumIonVerifiedOn;
  private List<ItemUpdateGtinAttribute> dcGtinAttributeList;
  private String hazmatVerifiedOn;
}
