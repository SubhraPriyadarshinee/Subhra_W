package com.walmart.move.nim.receiving.wfs.mock.data;

import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import java.util.Date;

public class MockDockTag {

  public static DockTag getDockTag() {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId("sysadmin");
    dockTag.setCreateTs(new Date());
    dockTag.setDeliveryNumber(12340001L);
    dockTag.setDockTagId("c32987000000000000000001");
    dockTag.setDockTagStatus(InstructionStatus.CREATED);
    return dockTag;
  }

  public static DockTag getDockTag2() {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId("rcvuser");
    dockTag.setCreateTs(new Date());
    dockTag.setDeliveryNumber(12340002L);
    dockTag.setDockTagId("c32987000000000000000002");
    dockTag.setDockTagStatus(InstructionStatus.CREATED);
    return dockTag;
  }

  public static DockTag getCompletedDockTag() {
    DockTag dockTag = getDockTag();
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteTs(new Date());
    dockTag.setCompleteUserId("sysadmin");
    return dockTag;
  }
}
