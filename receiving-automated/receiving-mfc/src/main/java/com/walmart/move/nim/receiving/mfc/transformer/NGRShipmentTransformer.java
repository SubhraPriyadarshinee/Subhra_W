package com.walmart.move.nim.receiving.mfc.transformer;

import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Destination;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Source;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.StatusInformation;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.List;
import java.util.Optional;

/** Transformer to convert ASNDocument to NGRShipment POJO and vice versa */
public class NGRShipmentTransformer implements Transformer<ASNDocument, NGRShipment> {
  @Override
  public void observe() {
    Transformer.super.observe();
  }

  @Override
  public NGRShipment transform(ASNDocument asnDocument) {
    return NGRShipment.builder()
        .delivery(getDelivery(asnDocument))
        .shipment(getShipment(asnDocument))
        .build();
  }

  private Delivery getDelivery(ASNDocument asnDocument) {
    return Optional.ofNullable(asnDocument)
        .map(SsccScanResponse::getDelivery)
        .map(
            delivery ->
                Delivery.builder()
                    .deliveryNumber(delivery.getDeliveryNumber())
                    .scheduled(delivery.getScheduled())
                    .arrivalTimeStamp(delivery.getArrivalTimeStamp())
                    .statusInformation(getStatusInformation(delivery.getStatusInformation()))
                    .build())
        .orElse(null);
  }

  private StatusInformation getStatusInformation(StatusInformation statusInformation) {
    return Optional.ofNullable(statusInformation)
        // Shipment Arrival message to NGR is only on ARV status
        .map(s -> StatusInformation.builder().status(DeliveryStatus.ARV.toString()).build())
        .orElse(null);
  }

  private Shipment getShipment(ASNDocument asnDocument) {
    return Optional.ofNullable(asnDocument)
        .map(ASNDocument::getShipment)
        .map(
            shipment ->
                Shipment.builder()
                    .source(getSource(shipment.getSource()))
                    .destination(getDestination(shipment.getDestination()))
                    .shipmentDetail(getShipmentDetails(shipment.getShipmentDetail()))
                    .additionalInfo(shipment.getAdditionalInfo())
                    .build())
        .orElse(null);
  }

  private Source getSource(Source source) {
    return Optional.ofNullable(source)
        .map(
            s ->
                Source.builder()
                    .number(s.getNumber())
                    .numberType(s.getNumberType())
                    .type(s.getType())
                    .countryCode(s.getCountryCode())
                    .shipperName(s.getShipperName())
                    .build())
        .orElse(null);
  }

  private Destination getDestination(Destination destination) {
    return Optional.ofNullable(destination)
        .map(
            d ->
                Destination.builder()
                    .type(d.getType())
                    .number(d.getNumber())
                    .countryCode(d.getCountryCode())
                    .build())
        .orElse(null);
  }

  private ShipmentDetails getShipmentDetails(ShipmentDetails shipmentDetail) {
    return Optional.ofNullable(shipmentDetail)
        .map(
            s ->
                ShipmentDetails.builder()
                    .trailerNumber(s.getTrailerNumber())
                    .loadNumber(s.getLoadNumber())
                    .build())
        .orElse(null);
  }

  @Override
  public List<NGRShipment> transformList(List<ASNDocument> asnDocuments) {
    return null;
  }

  @Override
  public ASNDocument reverseTransform(NGRShipment ngrShipment) {
    return null;
  }

  @Override
  public List<ASNDocument> reverseTransformList(List<NGRShipment> d) {
    return null;
  }
}
