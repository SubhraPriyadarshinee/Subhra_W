package com.walmart.move.nim.receiving.utils.common;

import java.util.HashMap;
import java.util.Map;

public class GdmToDCFinChannelMethodResolver {

  private static final Map<String, String> gdmToDcFinChannelMap = new HashMap<>();

  static {
    gdmToDcFinChannelMap.put("CROSSU", "Crossdock");
    gdmToDcFinChannelMap.put("CROSSMU", "Crossdock");
    gdmToDcFinChannelMap.put("SSTKU", "Staplestock");
    gdmToDcFinChannelMap.put("STAPLESTOCK", "Staplestock");
    gdmToDcFinChannelMap.put("CROSSNA", "Crossdock");
    gdmToDcFinChannelMap.put("CROSSNMA", "Crossdock");
    gdmToDcFinChannelMap.put("DSD", "Crossdock");
    gdmToDcFinChannelMap.put("DSDC", "DSDC");
    gdmToDcFinChannelMap.put("DSDS", "Crossdock");
    gdmToDcFinChannelMap.put("TWOTIER", "Crossdock");
    gdmToDcFinChannelMap.put("PAD", "Crossdock");
    gdmToDcFinChannelMap.put("GROCERYXDOCK", "Crossdock");
    gdmToDcFinChannelMap.put("SINGLE", "Staplestock");
    gdmToDcFinChannelMap.put("MULTI", "Crossdock");
    gdmToDcFinChannelMap.put("INV", "Crossdock");
    gdmToDcFinChannelMap.put("S2S", "Crossdock");
    gdmToDcFinChannelMap.put("POCON", "Crossdock");
    gdmToDcFinChannelMap.put("EXCEPTION", "Crossdock");
    gdmToDcFinChannelMap.put("CROSSDOCK", "Crossdock");
    gdmToDcFinChannelMap.put("STAPLESTOCK", "Staplestock");
  }

  public static String getDCFinChannelMethod(String gdmChannelMethod) {
    return gdmToDcFinChannelMap.get(gdmChannelMethod);
  }
}
