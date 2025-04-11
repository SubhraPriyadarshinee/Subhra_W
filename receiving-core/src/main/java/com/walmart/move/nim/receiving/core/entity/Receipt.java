/** */
package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

/** @author a0b02ft */
@Entity
@NamedQueries({
  @NamedQuery(
      name = "Receipt.receivedQtySummaryInEachesByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(palletQty), SUM(eachQty) as receivedQty) FROM Receipt WHERE deliveryNumber =:deliveryNumber AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtySummaryInVnpkByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE deliveryNumber =:deliveryNumber AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtySummaryByPoInVnpkByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE deliveryNumber =:deliveryNumber AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY purchaseReferenceNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtySummaryByPoLineInVnpkByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE deliveryNumber =:deliveryNumber AND purchaseReferenceNumber = :purchaseReferenceNumber AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByPoAndPoLine",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND purchaseReferenceNumber = :purchaseReferenceNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByPoAndPoLineInEach",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(eachQty) as receivedQty) FROM Receipt WHERE purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND purchaseReferenceNumber = :purchaseReferenceNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByProblemIdInVnpk",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByProblemIdResponse(problemId, SUM(quantity) as receivedQty) FROM Receipt WHERE problemId = :problemId AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY problemId"),
  @NamedQuery(
      name = "Receipt.receivedQtyByProblemIdInEa",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByProblemIdResponse(problemId, SUM(eachQty) as receivedQty) FROM Receipt WHERE problemId = :problemId AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY problemId"),
  @NamedQuery(
      name = "Receipt.receivedQtyByPoAndPoLineList",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(palletQty), SUM(eachQty) as receivedQty) FROM Receipt WHERE purchaseReferenceLineNumber IN :purchaseReferenceLineNumber AND purchaseReferenceNumber IN :purchaseReferenceNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByPoAndPoLineForDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(palletQty), SUM(eachQty) as receivedQty) "
              + "FROM Receipt "
              + "WHERE purchaseReferenceLineNumber IN :purchaseReferenceLineNumber "
              + "AND purchaseReferenceNumber IN :purchaseReferenceNumber "
              + "AND deliveryNumber = :deliveryNumber "
              + "GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByPoAndPoLineListWithoutDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE purchaseReferenceLineNumber IN :purchaseReferenceLineNumber AND purchaseReferenceNumber IN :purchaseReferenceNumber AND deliveryNumber <> :deliveryNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByPoWithDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndDeliveryResponse(purchaseReferenceNumber,SUM(quantity) as receivedQty) FROM Receipt WHERE purchaseReferenceNumber = :purchaseReferenceNumber AND deliveryNumber =:deliveryNumber GROUP BY purchaseReferenceNumber,deliveryNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyAndPalletQtyByPoWithDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse(purchaseReferenceNumber,SUM(quantity) as receivedQty, SUM(palletQty) as palletQty) FROM Receipt WHERE purchaseReferenceNumber IN :purchaseReferenceNumbers AND deliveryNumber =:deliveryNumber GROUP BY purchaseReferenceNumber,deliveryNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByDeliveryPoAndPoLine",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND purchaseReferenceNumber = :purchaseReferenceNumber AND deliveryNumber = :deliveryNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByDeliveryPoAndPoLineInEaches",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(eachQty) as receivedQty) FROM Receipt WHERE purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND purchaseReferenceNumber = :purchaseReferenceNumber AND deliveryNumber = :deliveryNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtySummaryByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, purchaseReferenceLineNumber,vnpkQty,whpkQty,quantityUom, SUM(quantity) as receivedQty) FROM Receipt WHERE deliveryNumber = :deliveryNumber AND quantityUom is not null GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber, vnpkQty, whpkQty,quantityUom"),
  @NamedQuery(
      name = "Receipt.receiptSummaryByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, purchaseReferenceLineNumber,quantityUom, SUM(quantity) as receivedQty,SUM(orderFilledQuantity) as orderFilledQty) FROM Receipt WHERE deliveryNumber = :deliveryNumber AND quantityUom is not null GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber,quantityUom"),
  @NamedQuery(
      name = "Receipt.receivedQtyInVNPKByPoAndPoLineList",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE purchaseReferenceNumber IN :purchaseReferenceNumber AND purchaseReferenceLineNumber IN :purchaseReferenceLineNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyInEaByPoAndPoLineList",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(eachQty) as receivedQty) FROM Receipt WHERE purchaseReferenceNumber IN :purchaseReferenceNumber AND purchaseReferenceLineNumber IN :purchaseReferenceLineNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyInVNPKByDeliveryPoAndPoLineList",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(quantity) as receivedQty) FROM Receipt WHERE purchaseReferenceNumber IN :purchaseReferenceNumber AND purchaseReferenceLineNumber IN :purchaseReferenceLineNumber AND deliveryNumber = :deliveryNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyInEaByDeliveryPoAndPoLineList",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, SUM(eachQty) as receivedQty) FROM Receipt WHERE purchaseReferenceNumber IN :purchaseReferenceNumber AND purchaseReferenceLineNumber IN :purchaseReferenceLineNumber AND deliveryNumber = :deliveryNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtyByDeliveryPoAndPoLineAndSSCC",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.RxReceiptsSummaryResponse(purchaseReferenceNumber, purchaseReferenceLineNumber, ssccNumber, SUM(quantity) as receivedQty, SUM(eachQty) as receivedQtyInEA) FROM Receipt WHERE deliveryNumber = :deliveryNumber AND purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND purchaseReferenceNumber = :purchaseReferenceNumber AND ssccNumber = :ssccNumber GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber, ssccNumber"),
  @NamedQuery(
      name = "Receipt.receivedQtySummaryInEAByDelivery",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, purchaseReferenceLineNumber,vnpkQty,whpkQty,quantityUom, SUM(eachQty) as receivedQty) FROM Receipt WHERE deliveryNumber = :deliveryNumber AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber, vnpkQty, whpkQty,quantityUom"),
  @NamedQuery(
      name = "Receipt.receivedQtySummaryInEachesByDeliveryAndPo",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, purchaseReferenceLineNumber,vnpkQty,whpkQty,quantityUom, SUM(eachQty) as receivedQty) FROM Receipt WHERE deliveryNumber = :deliveryNumber AND purchaseReferenceNumber = :purchaseReferenceNumber AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode GROUP BY purchaseReferenceNumber, purchaseReferenceLineNumber, vnpkQty, whpkQty,quantityUom"),
  @NamedQuery(
      name = "Receipt.receivedQtyInEAByShipmentNumberForPoPoLine",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse(SUM(eachQty) as receivedQty, inboundShipmentDocId) FROM Receipt WHERE purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND purchaseReferenceNumber = :purchaseReferenceNumber GROUP BY inboundShipmentDocId"),
  @NamedQuery(
      name = "Receipt.receiptsSummaryByDeliveries",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(deliveryNumber, SUM(quantity) as receivedQty, quantityUom) FROM Receipt WHERE deliveryNumber IN (:deliveryNumbers) AND quantityUom is not null GROUP BY deliveryNumber, quantityUom"),
  @NamedQuery(
      name = "Receipt.receiptsSummaryByPoNumbers",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber, SUM(quantity) as receivedQty, quantityUom) FROM Receipt WHERE purchaseReferenceNumber IN (:poNumbers) AND quantityUom is not null GROUP BY purchaseReferenceNumber, quantityUom"),
  @NamedQuery(
      name = "Receipt.receivedPacksByDeliveryAndPo",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse(purchaseReferenceNumber,CAST(COUNT(DISTINCT ssccNumber) AS integer) as rcvdPackCount) FROM Receipt WHERE deliveryNumber = :deliveryNumber AND ssccNumber is not NULL GROUP BY purchaseReferenceNumber"),
})
@Table(name = "RECEIPT")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@DynamicUpdate
public class Receipt extends BaseMTEntity implements Serializable {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "receiptSequence")
  @SequenceGenerator(
      name = "receiptSequence",
      sequenceName = "receipt_sequence",
      allocationSize = 100)
  private Long id;

  @Column(name = "DELIVERY_NUMBER", nullable = false)
  private Long deliveryNumber;

  @Column(name = "DOOR_NUMBER")
  private String doorNumber;

  @Column(name = "PURCHASE_REFERENCE_NUMBER", length = 20, nullable = false)
  private String purchaseReferenceNumber;

  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private Integer purchaseReferenceLineNumber;

  @Column(name = "QUANTITY")
  private Integer quantity;

  @Column(length = 8, name = "QUANTITY_UOM")
  private String quantityUom;

  @Column(name = "VNPK_QTY")
  private Integer vnpkQty;

  @Column(name = "WHPK_QTY")
  private Integer whpkQty;

  @Column(name = "EACH_QTY")
  private Integer eachQty;

  @Column(name = "PROBLEM_ID", length = 40)
  private String problemId;

  @Column(length = 32, name = "CREATE_USER_ID")
  private String createUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS", nullable = false, updatable = false)
  private Date createTs;

  @Column(name = "FB_OVER_QTY")
  private Integer fbOverQty;

  @Column(name = "FB_OVER_QTY_UOM", length = 8)
  private String fbOverQtyUOM;

  @Enumerated(EnumType.STRING)
  @Column(name = "FB_OVER_REASON_CODE", length = 8)
  private OSDRCode fbOverReasonCode;

  @Column(name = "FB_SHORT_QTY")
  private Integer fbShortQty;

  @Column(name = "FB_SHORT_QTY_UOM", length = 8)
  private String fbShortQtyUOM;

  @Enumerated(EnumType.STRING)
  @Column(name = "FB_SHORT_REASON_CODE", length = 8)
  private OSDRCode fbShortReasonCode;

  @Column(name = "FB_DAMAGED_QTY")
  private Integer fbDamagedQty;

  @Column(name = "FB_DAMAGED_QTY_UOM")
  private String fbDamagedQtyUOM;

  @Enumerated(EnumType.STRING)
  @Column(name = "FB_DAMAGED_REASON_CODE", length = 8)
  private OSDRCode fbDamagedReasonCode;

  @Column(name = "FB_REJECTED_QTY")
  private Integer fbRejectedQty;

  @Column(name = "FB_REJECTED_QTY_UOM", length = 8)
  private String fbRejectedQtyUOM;

  @Enumerated(EnumType.STRING)
  @Column(name = "FB_REJECTED_REASON_CODE", length = 8)
  private OSDRCode fbRejectedReasonCode;

  @Column(name = "FB_CONCEALED_SHORTAGE_QTY")
  private Integer fbConcealedShortageQty;

  @Enumerated(EnumType.STRING)
  @Column(name = "FB_CONCEALED_SHORTAGE_REASON_CODE", length = 8)
  private OSDRCode fbConcealedShortageReasonCode;

  @Column(name = "FB_PROBLEM_QTY")
  private Integer fbProblemQty;

  @Column(name = "FB_PROBLEM_QTY_UOM", length = 8)
  private String fbProblemQtyUOM;

  @Column(name = "FB_REJECTION_COMMENT", length = 255)
  private String fbRejectionComment;

  @Column(length = 32, name = "FINALIZED_USER_ID")
  private String finalizedUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "FINALIZE_TS")
  private Date finalizeTs;

  @Column(name = "FB_DAMAGED_CLAIM_TYPE", length = 32)
  private String fbDamagedClaimType;

  @Version
  @Column(name = "VERSION")
  private Integer version;

  @Column(name = "OSDR_MASTER")
  private Integer osdrMaster;

  @Column(name = "PALLET_QTY")
  private Integer palletQty;

  @Column(name = "SSCC_NUMBER", length = 40)
  private String ssccNumber;

  @Column(name = "SHIPMENT_DOCUMENT_ID", length = 255)
  private String inboundShipmentDocId;

  @Column(name = "ORDER_FILLED_QUANTITY")
  private Integer orderFilledQuantity;

  @Expose
  @Column(name = "INVOICE_NUMBER", length = 50)
  private String invoiceNumber;

  @Expose
  @Column(name = "INVOICE_LINE_NUMBER")
  private Integer invoiceLineNumber;

  @Expose
  @Column(name = "SUBCENTER_ID")
  private Integer orgUnitId;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
    this.lastChangedTs = new Date();
  }
}
