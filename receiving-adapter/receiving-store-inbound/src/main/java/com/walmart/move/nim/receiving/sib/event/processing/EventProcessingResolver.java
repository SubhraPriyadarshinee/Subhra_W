package com.walmart.move.nim.receiving.sib.event.processing;

import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.model.FreightType;
import com.walmart.move.nim.receiving.sib.service.FreightTypeResolver;
import org.springframework.beans.factory.annotation.Autowired;

public class EventProcessingResolver {

  @Autowired private MeatProduceEventProcessing meatProduceEventProcessing;

  @Autowired private NHMEventProcessing nhmEventProcessing;

  @Autowired private SuperCentreEventProcessing superCentreEventProcessing;

  public EventProcessing resolve(ContainerDTO containerDTO) {

    FreightType freightType = FreightTypeResolver.resolveFreightType(containerDTO);

    return getEventProcessing(freightType);
  }

  public EventProcessing resolve(FreightType freightType) {

    return getEventProcessing(freightType);
  }

  private EventProcessing getEventProcessing(FreightType freightType) {
    switch (freightType) {
      case NHM:
        return nhmEventProcessing;
      case MEAT_PRODUCE:
        return meatProduceEventProcessing;
      default:
        return superCentreEventProcessing;
    }
  }
}
