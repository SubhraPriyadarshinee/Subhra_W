package com.walmart.move.nim.receiving.utils.common;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class GdmToDCFinChannelMethodResolverTest {

  @Test
  public void testGetDCFinChannelMethod() {
    assertEquals(GdmToDCFinChannelMethodResolver.getDCFinChannelMethod("CROSSU"), "Crossdock");
    assertEquals(GdmToDCFinChannelMethodResolver.getDCFinChannelMethod("SSTKU"), "Staplestock");
    assertEquals(GdmToDCFinChannelMethodResolver.getDCFinChannelMethod("DSDC"), "DSDC");
    assertEquals(GdmToDCFinChannelMethodResolver.getDCFinChannelMethod("EXCEPTION"), "Crossdock");
    assertEquals(GdmToDCFinChannelMethodResolver.getDCFinChannelMethod("SINGLE"), "Staplestock");
  }
}
