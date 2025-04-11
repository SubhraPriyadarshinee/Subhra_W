package com.walmart.move.nim.receiving.core.entity;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "ASN_METADATA")
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ASNMetaData extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ASN_METADATA_SEQUENCE")
  @SequenceGenerator(
      name = "ASN_METADATA_SEQUENCE",
      sequenceName = "ASN_METADATA_SEQUENCE",
      allocationSize = 50)
  @Column(name = "ID")
  private long id;

  @Version
  @Column(name = "VERSION")
  private long version;

  @Column(name = "DELIVERY_NUMBER")
  private String deliveryNumber;

  @Column(name = "ASN_ID")
  private String asnId;

  @Column(name = "ASN_DOC_ID")
  private String originDocId;

  @Column(name = "ASN_SHIPMENT_ID")
  private String shipmentId;

  @Column(name = "ASN_TYPE")
  private String type; // Vendor or node
}
