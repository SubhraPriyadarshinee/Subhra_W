package com.walmart.move.nim.receiving.core.model.ngr;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NGRPack extends MessageData {

  String inboundDocumentId;
  String receivingDeliveryId;
  String inboundDocumentPackId;
  String receivingDeliverySystem;
  String receivingDeliveryType;
  String receivingUserId;
  String packDSCSA;
  String inboundDocumentRevision;
  String packNumber;
  String documentPackNumber;
  String palletNumber;
  String documentNumber;
  String documentType;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ReceivingConstants.UTC_DATE_FORMAT)
  Date receivingFinalizedDate;

  String invoiceNumber;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd")
  Date invoiceDate;

  String sourceNumber;
  SourceType sourceType;
  String sourceCountryCode;
  String destinationNumber;
  SourceType destinationType;
  String destinationCountryCode;
  List<PackItem> items;
}
