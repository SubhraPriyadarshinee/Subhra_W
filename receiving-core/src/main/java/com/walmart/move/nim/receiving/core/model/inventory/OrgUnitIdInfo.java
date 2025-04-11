package com.walmart.move.nim.receiving.core.model.inventory;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrgUnitIdInfo {
  @Expose Integer sourceId;
  @Expose Integer destinationId;
}
