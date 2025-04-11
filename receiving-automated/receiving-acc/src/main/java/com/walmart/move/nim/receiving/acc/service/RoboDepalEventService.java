package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.model.docktag.ReceiveDockTagRequest;
import com.walmart.move.nim.receiving.core.service.DockTagPersisterService;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;

public class RoboDepalEventService {
  @Autowired
  @Qualifier(ACCConstants.ACC_DOCK_TAG_SERVICE)
  private DockTagService dockTagService;

  @Autowired private DockTagPersisterService dockTagPersisterService;

  public void processDepalAckEvent(
      ReceiveDockTagRequest receiveDockTagRequest, HttpHeaders httpHeaders) {
    final String dockTagId = receiveDockTagRequest.getDockTagId();
    DockTag dockTagFromDb = dockTagPersisterService.getDockTagByDockTagId(dockTagId);
    dockTagService.validateDockTagFromDb(
        dockTagFromDb, dockTagId, ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE, httpHeaders);
    dockTagService.updateDockTagStatusAndPublish(receiveDockTagRequest, dockTagFromDb, httpHeaders);
  }

  public void processDepalFinishEvent(
      ReceiveDockTagRequest receiveDockTagRequest, HttpHeaders httpHeaders) {
    final String dockTagId = receiveDockTagRequest.getDockTagId();
    dockTagService.completeDockTag(dockTagId, httpHeaders);
  }
}
