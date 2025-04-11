package com.walmart.move.nim.receiving.core.message.common;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class PalletDataTest {

  @Test
  public void testTestEquals() {
    PalletData palletData = PalletData.builder().palletNumber("1").build();
    PalletData palletData2 = PalletData.builder().palletNumber("1").build();
    assertEquals(palletData2, palletData);
    assertEquals(palletData2.hashCode(), palletData.hashCode());
  }

  @Test
  public void testTestToString() {
    PalletData palletData = new PalletData();
    palletData.setPalletNumber("1");
    assertNotNull(palletData.toString());
  }

  @Test
  public void testBuilder() {
    PalletData palletData = PalletData.builder().palletNumber("1").build();
    assertNotNull(palletData.getPalletNumber());
  }
}
