package com.walmart.move.nim.receiving.rdc.mock.data;

import com.walmart.move.nim.receiving.core.entity.DockTag;
import java.util.Date;

public class MockDockTagData {

  public static DockTag mockDockTag() {
    DockTag dt = new DockTag();
    dt.setDeliveryNumber(12345678L);
    dt.setDockTagId("DT215489654712545845");
    dt.setCreateUserId("sysadmin");
    dt.setCreateTs(new Date());
    return dt;
  }
}
