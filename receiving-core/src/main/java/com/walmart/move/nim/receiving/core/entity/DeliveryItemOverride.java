package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "DELIVERY_ITEM_OVERRIDE")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@IdClass(DeliveryItemOverrideId.class)
public class DeliveryItemOverride extends BaseMTEntity {

  private static final long serialVersionUID = 1L;

  @Id
  @Expose
  @NotNull
  @Column(name = "DELIVERY_NUMBER", length = 20)
  private Long deliveryNumber;

  @Id
  @Expose
  @NotNull
  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;

  @Expose
  @Column(name = "TEMP_PALLET_TI")
  private Integer tempPalletTi;

  @Expose
  @Column(name = "TEMP_PALLET_HI")
  private Integer tempPalletHi;

  @Expose
  @Version
  @Column(name = "VERSION")
  private Integer version;

  @Expose
  @Column(name = "ITEM_MISC_INFO", length = 600)
  @Convert(converter = JpaConverterJson.class)
  private Map<String, String> itemMiscInfo;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @Expose
  @Column(name = "LAST_CHANGED_USER", length = 20)
  private String lastChangedUser;

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getLastChangedTs())) this.lastChangedTs = new Date();
  }
}
