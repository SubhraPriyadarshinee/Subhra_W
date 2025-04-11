package com.walmart.move.nim.receiving.core.message.common;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class PackDataTest {

  @Test
  public void testTestEquals() {
    PackData packData = PackData.builder().documentPackId("1").build();
    PackData packData2 = PackData.builder().documentPackId("1").build();
    assertEquals(packData2, packData);
    assertEquals(packData2.hashCode(), packData.hashCode());
  }

  @Test
  public void testTestToString() {
    PackData packData = new PackData();
    assertNotNull(packData.toString());
  }

  @Test
  public void testBuilder() {
    PackData packData = PackData.builder().documentPackId("1").build();
    assertNotNull(packData.getDocumentPackId());
  }
}
