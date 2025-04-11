package com.walmart.move.nim.receiving.core.message.common;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class PackItemDataTest {

  @Test
  public void testTestEquals() {
    PackItemData packItemData = PackItemData.builder().documentPackId("1").build();
    PackItemData packItemData2 = PackItemData.builder().documentPackId("1").build();
    assertEquals(packItemData2, packItemData);
    assertEquals(packItemData2.hashCode(), packItemData.hashCode());
  }

  @Test
  public void testTestToString() {
    PackItemData packItemData = new PackItemData();
    assertNotNull(packItemData.toString());
  }

  @Test
  public void testBuilder() {
    PackItemData packItemData = PackItemData.builder().documentPackId("1").build();
    assertNotNull(packItemData.getDocumentPackId());
  }
}
