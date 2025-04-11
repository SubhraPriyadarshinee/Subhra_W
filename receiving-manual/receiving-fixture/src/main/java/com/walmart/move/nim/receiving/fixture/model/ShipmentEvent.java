package com.walmart.move.nim.receiving.fixture.model;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import java.util.List;
import java.util.Objects;

public class ShipmentEvent {
  String eventType;
  List<Pack> packList;
  Shipment shipment;

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public List<Pack> getPackList() {
    return packList;
  }

  public void setPackList(List<Pack> packList) {
    this.packList = packList;
  }

  public Shipment getShipment() {
    return shipment;
  }

  public void setShipment(Shipment shipment) {
    this.shipment = shipment;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("ShipmentEvent{");
    sb.append("eventType=").append(eventType);
    if (Objects.nonNull(shipment)) {
      sb.append("shipment=").append(shipment.getShipmentNumber());
      sb.append("packListSize=").append(shipment.getTotalPacks());
    }
    sb.append('}');
    return sb.toString();
  }
}
