package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import javax.persistence.Column;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class DeliveryItemOverrideId extends BaseMTEntity implements Serializable {

  @Expose
  @NotNull
  @Column(name = "DELIVERY_NUMBER", length = 20)
  private Long deliveryNumber;

  @Expose
  @NotNull
  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;
}
