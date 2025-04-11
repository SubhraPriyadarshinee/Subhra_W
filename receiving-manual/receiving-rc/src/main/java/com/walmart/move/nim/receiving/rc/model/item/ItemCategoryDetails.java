package com.walmart.move.nim.receiving.rc.model.item;

import lombok.*;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemCategoryDetails {
  private String prePopulatedCategory;
  private String chosenCategory;
}
