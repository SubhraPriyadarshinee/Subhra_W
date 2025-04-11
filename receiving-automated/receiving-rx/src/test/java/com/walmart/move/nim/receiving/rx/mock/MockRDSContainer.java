package com.walmart.move.nim.receiving.rx.mock;

import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import java.util.Arrays;

public class MockRDSContainer {

  public static ReceiveContainersResponseBody mockRdsContainer() {

    ReceiveContainersResponseBody slotDetails = new ReceiveContainersResponseBody();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setReceiver(971234567);
    Destination destination = new Destination();
    destination.setSlot("MOCK_UNIT_TRACKING_ID");
    receivedContainer.setDestinations(Arrays.asList(destination));
    receivedContainer.setLabelTrackingId("009700936505");
    slotDetails.setReceived(Arrays.asList(receivedContainer));
    return slotDetails;
  }
}
