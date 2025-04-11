package com.walmart.move.nim.receiving.core.model.gdm.v3;

import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class QuantityDetail {
  @NotEmpty private String uom;
  @NotEmpty private Integer quantity;
}
