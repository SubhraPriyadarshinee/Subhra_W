package com.walmart.move.nim.receiving.rc.entity;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class ProductCategoryGroupPK implements Serializable {

  private static final long serialVersionUID = 8420785238835617361L;

  @Column(name = "L0", length = 256, nullable = false)
  private String l0;

  @Column(name = "L1", length = 256, nullable = false)
  private String l1;

  @Column(name = "L2", length = 256, nullable = false)
  private String l2;

  @Column(name = "L3", length = 256, nullable = false)
  private String l3;

  @Column(name = "PRODUCT_TYPE", length = 256, nullable = false)
  private String productType;
}
