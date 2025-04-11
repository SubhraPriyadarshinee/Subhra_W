package com.walmart.move.nim.receiving.core.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * Entity class to map instruction data
 *
 * @author v0k00fe
 */
@Data
@Entity
@Table(name = "DELIVERY_PO_LINE")
public class DeliveryPoLine {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "deliveryPoLineSequence")
  @SequenceGenerator(
      name = "deliveryPoLineSequence",
      sequenceName = "delivery_po_line_sequence",
      allocationSize = 50)
  private Long id;

  private String facilityCountryCode;
  private Integer facilityNum;
  @NotNull private Long deliveryNumber;
  @NotNull private String purchaseReferenceNumber;
  @NotNull private Integer purchaseReferenceLineNumber;
  @NotNull private Integer quantity;

  private String quantityUOM;

  private Integer eachQty;
  private Integer whpkQty;
  private Integer vnpkQty;
  private Integer fbOverQty;
  private String fbOverQtyUOM;
  private String fbOverReasonCode;
  private Integer fbShortQty;
  private String fbShortQtyUOM;
  private String fbShortReasonCode;
  private Integer fbDamagedQty;
  private String fbDamagedQtyUOM;
  private String fbDamagedReasonCode;
  private Integer fbRejectedQty;
  private String fbRejectedQtyUOM;
  private String fbRejectedReasonCode;
  private Integer fbConcealedShortageQty;
  private String fbConcealedShortageReasonCode;
}
