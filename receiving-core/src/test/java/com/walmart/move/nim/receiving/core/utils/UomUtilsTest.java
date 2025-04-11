package com.walmart.move.nim.receiving.core.utils;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.util.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

public class UomUtilsTest extends ReceivingTestBase {

  private List<String> scalableUomList = Arrays.asList("LB", "OZ");

  @Test
  public void testScaling() {
    Pair<Integer, String> milliScaledQuantity =
        UomUtils.getScaledQuantity(12.3456, "LB", "milli", scalableUomList);
    Pair<Integer, String> centiScaledQuantity =
        UomUtils.getScaledQuantity(12.3456, "OZ", "centi", scalableUomList);
    Pair<Integer, String> deciScaledQuantity =
        UomUtils.getScaledQuantity(12.3456, "LB", "deci", scalableUomList);
    Assert.assertEquals(milliScaledQuantity.getFirst().intValue(), 12345);
    Assert.assertEquals(centiScaledQuantity.getFirst().intValue(), 1234);
    Assert.assertEquals(deciScaledQuantity.getFirst().intValue(), 123);

    Assert.assertEquals(milliScaledQuantity.getSecond(), "milli-LB");
    Assert.assertEquals(centiScaledQuantity.getSecond(), "centi-OZ");
    Assert.assertEquals(deciScaledQuantity.getSecond(), "deci-LB");

    milliScaledQuantity = UomUtils.getScaledQuantity(5.1, "LB", "milli", scalableUomList);
    centiScaledQuantity = UomUtils.getScaledQuantity(5.1, "OZ", "centi", scalableUomList);
    deciScaledQuantity = UomUtils.getScaledQuantity(5.1, "LB", "deci", scalableUomList);
    Assert.assertEquals(milliScaledQuantity.getFirst().intValue(), 5100);
    Assert.assertEquals(centiScaledQuantity.getFirst().intValue(), 510);
    Assert.assertEquals(deciScaledQuantity.getFirst().intValue(), 51);

    // Invalid scenario. Resulting in incorrect value and data loss
    Pair<Integer, String> unscaledQuantity =
        UomUtils.getScaledQuantity(12.3456, "EA", "deci", scalableUomList);
    Assert.assertEquals(unscaledQuantity.getFirst().intValue(), 12);
    Assert.assertEquals(unscaledQuantity.getSecond(), "EA");

    Pair<Integer, String> nullQuantity =
        UomUtils.getScaledQuantity(null, "LB", "deci", scalableUomList);
    Assert.assertNull(nullQuantity);

    Pair<Integer, String> defaultQuantity =
        UomUtils.getScaledQuantity(null, "LB", "deci", scalableUomList, 0);
    Assert.assertEquals(defaultQuantity.getFirst().intValue(), 0);
    Assert.assertEquals(defaultQuantity.getSecond(), "deci-LB");
  }

  @Test
  public void testDerivedQty() {
    Pair<Double, String> milliBaseQuantity = UomUtils.getBaseUnitQuantity(12345, "milli-OZ");
    Pair<Double, String> centiBaseQuantity = UomUtils.getBaseUnitQuantity(1234, "centi-LB");
    Pair<Double, String> deciBaseQuantity = UomUtils.getBaseUnitQuantity(123L, "deci-OZ");
    Assert.assertEquals(milliBaseQuantity.getFirst(), 12.345);
    Assert.assertEquals(centiBaseQuantity.getFirst(), 12.34);
    Assert.assertEquals(deciBaseQuantity.getFirst(), 12.3);
    Assert.assertEquals(milliBaseQuantity.getSecond(), "OZ");
    Assert.assertEquals(centiBaseQuantity.getSecond(), "LB");
    Assert.assertEquals(deciBaseQuantity.getSecond(), "OZ");

    Pair<Double, String> baseQuantity = UomUtils.getBaseUnitQuantity(123456, "ZA");
    Assert.assertEquals(baseQuantity.getFirst(), 123456.0);
    Assert.assertEquals(baseQuantity.getSecond(), "ZA");

    Integer nullQty = null;
    Pair<Double, String> nullQuantity = UomUtils.getBaseUnitQuantity(nullQty, "deci-LB");
    Assert.assertNull(nullQuantity);

    Pair<Double, String> defaultQuantity = UomUtils.getBaseUnitQuantity(nullQty, "deci-LB", 0);
    Assert.assertEquals(defaultQuantity.getFirst(), 0.0);
  }
}
