package com.walmart.move.nim.receiving.fixture.utils;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class FixtureMMUtils {

  public static Map<String, Object> constructMMPayload(
      String fromLocation,
      Container containerDetails,
      SlottingPalletResponse slottingPalletResponse,
      HttpHeaders httpHeaders,
      FixtureManagedConfig fixtureManagedConfig) {

    Map<String, Object> moveInfo = new LinkedTreeMap<>();

    moveInfo.put(ReceivingConstants.MOVE_EVENT, MoveEvent.CREATE.getMoveEvent());
    moveInfo.put(ReceivingConstants.MOVE_CONTAINER_TAG, containerDetails.getTrackingId());
    moveInfo.put(ReceivingConstants.MOVE_FROM_LOCATION, fromLocation);
    moveInfo.put(
        ReceivingConstants.MOVE_CORRELATION_ID,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveInfo.put(
        ReceivingConstants.MOVE_QTY, containerDetails.getContainerItems().get(0).getQuantity());
    moveInfo.put(
        ReceivingConstants.MOVE_TO_LOCATION,
        slottingPalletResponse.getLocations().get(0).getLocation());
    moveInfo.put(ReceivingConstants.MOVE_QTY_UOM, fixtureManagedConfig.getMoveQtyUom());
    moveInfo.put(ReceivingConstants.MOVE_SEQUENCE_NBR, 1);
    moveInfo.put(ReceivingConstants.MOVE_TYPE_CODE, fixtureManagedConfig.getRfcMoveTypeCode());
    moveInfo.put(ReceivingConstants.MOVE_TYPE_DESC, fixtureManagedConfig.getRfcMoveTypeDesc());
    return moveInfo;
  }
}
