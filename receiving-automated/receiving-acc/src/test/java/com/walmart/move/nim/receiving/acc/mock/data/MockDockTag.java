package com.walmart.move.nim.receiving.acc.mock.data;

import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MockDockTag {

  public static DockTag getDockTag() {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId("sysadmin");
    dockTag.setCreateTs(new Date());
    dockTag.setLastChangedTs(new Date());
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

  public static List<DockTag> getMultiManifestDockTags() {
    List<DockTag> multiManifestDockTags = new ArrayList<>();
    DockTag dockTag1 = getDockTag();
    DockTag dockTag2 = getDockTag2();
    dockTag1.setWorkstationLocation("WS0002");
    dockTag1.setLastChangedTs(new Date());
    dockTag2.setWorkstationLocation("WS0001");
    dockTag2.setLastChangedTs(new Date());
    multiManifestDockTags.add(dockTag1);
    multiManifestDockTags.add(dockTag2);
    return multiManifestDockTags;
  }

  public static List<DockTag> getMultiManifestDockTags_SameDelivery() {
    List<DockTag> multiManifestDockTags = new ArrayList<>();
    DockTag dockTag1 = getDockTag();
    DockTag dockTag2 = getDockTag2();
    dockTag1.setWorkstationLocation("WS0002");
    dockTag1.setLastChangedTs(new Date());
    dockTag2.setWorkstationLocation("WS0001");
    dockTag2.setDeliveryNumber(12340001L);
    dockTag2.setLastChangedTs(new Date());
    multiManifestDockTags.add(dockTag1);
    multiManifestDockTags.add(dockTag2);
    return multiManifestDockTags;
  }

  public static List<DockTag> getMultiManifestDockTags_SameLocationAsScanned() {
    List<DockTag> multiManifestDockTags = new ArrayList<>();
    DockTag dockTag1 = getDockTag();
    DockTag dockTag2 = getDockTag2();
    dockTag1.setWorkstationLocation("WS0001");
    dockTag1.setLastChangedTs(new Date());
    dockTag2.setWorkstationLocation("WS0001");
    dockTag2.setLastChangedTs(new Date());
    multiManifestDockTags.add(dockTag1);
    multiManifestDockTags.add(dockTag2);
    return multiManifestDockTags;
  }

  public static DockTag getCompletedDockTag() {
    DockTag dockTag = getDockTag();
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteTs(new Date());
    dockTag.setCompleteUserId("sysadmin");
    return dockTag;
  }

  public static DockTag getNonConDockTag() {
    DockTag nonConDockTag = getDockTag();
    nonConDockTag.setDockTagType(DockTagType.NON_CON);
    nonConDockTag.setScannedLocation("PTR001");
    return nonConDockTag;
  }

  public static DockTag getCompletedNonConDockTag() {
    DockTag nonConDockTag = getCompletedDockTag();
    nonConDockTag.setDockTagType(DockTagType.NON_CON);
    nonConDockTag.setScannedLocation("PTR001");
    return nonConDockTag;
  }
}
