package com.walmart.move.nim.receiving.core.message.common;

import java.util.List;
import lombok.*;

/** @author k0a00vx */
@Getter
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class PackData {

  private String documentPackId;

  private String documentId;

  private String packNumber;

  private List<PackItemData> items;

  private String receivingStatus;

  private String shipmentNumber;

  private boolean multiskuPack;

  private boolean partialPack;

  private Integer auditStatus;
}
