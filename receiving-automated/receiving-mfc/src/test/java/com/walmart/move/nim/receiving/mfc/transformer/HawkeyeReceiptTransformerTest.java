package com.walmart.move.nim.receiving.mfc.transformer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import java.util.Map;
import java.util.stream.Collectors;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class HawkeyeReceiptTransformerTest {

  @InjectMocks private HawkeyeReceiptTransformer hawkeyeReceiptTransformer;

  @BeforeClass
  private void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testHawkeyeToCommonReceiptTransform() {
    HawkeyeAdjustment hawkeyeAdjustment =
        MFCTestUtils.getHawkeyeAdjustment("src/test/resources/autoMFC/hawkeyeAdjustment.json");
    CommonReceiptDTO commonReceiptDTO = hawkeyeReceiptTransformer.transform(hawkeyeAdjustment);
    assertNotNull(commonReceiptDTO);
    assertNotNull(commonReceiptDTO.getQuantities());
    assertEquals(commonReceiptDTO.getQuantities().size(), 6);
    Map<QuantityType, Long> quantityTypeMap =
        commonReceiptDTO
            .getQuantities()
            .stream()
            .collect(Collectors.groupingBy(Quantity::getType, Collectors.counting()));
    Long damageCount = quantityTypeMap.getOrDefault(QuantityType.DAMAGE, 0L);
    Long rejectCount =
        quantityTypeMap.getOrDefault(QuantityType.REJECTED, 0L)
            + quantityTypeMap.getOrDefault(QuantityType.MFCOVERSIZE, 0L)
            + quantityTypeMap.getOrDefault(QuantityType.NOTMFCASSORTMENT, 0L)
            + quantityTypeMap.getOrDefault(QuantityType.FRESHNESSEXPIRATION, 0L);
    Long decantCount = quantityTypeMap.getOrDefault(QuantityType.DECANTED, 0L);
    assertEquals((long) damageCount, 1);
    assertEquals((long) rejectCount, 4);
    assertEquals((long) decantCount, 1);
  }

  @Test
  public void testWrongTempZoneReject() {
    HawkeyeAdjustment hawkeyeAdjustment =
        MFCTestUtils.getHawkeyeAdjustment("src/test/resources/autoMFC/wrongTempZoneReject.json");
    CommonReceiptDTO commonReceiptDTO = hawkeyeReceiptTransformer.transform(hawkeyeAdjustment);
    assertNotNull(commonReceiptDTO);
    assertNotNull(commonReceiptDTO.getQuantities());
    assertEquals(commonReceiptDTO.getQuantities().size(), 1);
    assertEquals(commonReceiptDTO.getQuantities().get(0).getType(), QuantityType.WRONG_TEMP_ZONE);
    assertEquals(commonReceiptDTO.getQuantities().get(0).getValue().intValue(), 5);
  }
}
