package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.common.LabelType;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LabelSummary {
  @NotNull private LabelType type;
  @NotNull private long count;
}
