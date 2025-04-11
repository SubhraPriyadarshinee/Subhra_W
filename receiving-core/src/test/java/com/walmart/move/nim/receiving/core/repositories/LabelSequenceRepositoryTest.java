package com.walmart.move.nim.receiving.core.repositories;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.LabelSequence;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelSequenceRepositoryTest extends ReceivingTestBase {

  @Autowired private LabelSequenceRepository labelSequenceRepository;
  private List<LabelSequence> mockLabelSequenceList;
  Date mustArriveBeforeDate = new Date();

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    LabelSequence labelSequence1 =
        LabelSequence.builder()
            .itemNumber(876987l)
            .labelType(LabelType.ORDERED)
            .nextSequenceNo(220200420000800001L)
            .purchaseReferenceLineNumber(1)
            .mustArriveBeforeDate(mustArriveBeforeDate)
            .labelType(LabelType.ORDERED)
            .build();
    LabelSequence labelSequence2 =
        LabelSequence.builder()
            .itemNumber(876987l)
            .labelType(LabelType.OVERAGE)
            .nextSequenceNo(420200420000800001L)
            .mustArriveBeforeDate(mustArriveBeforeDate)
            .labelType(LabelType.OVERAGE)
            .purchaseReferenceLineNumber(1)
            .build();
    mockLabelSequenceList = Arrays.asList(labelSequence1, labelSequence2);
    labelSequenceRepository.saveAll(mockLabelSequenceList);
  }

  @Test
  public void findByMustArriveBeforeDateAndPurchaseReferenceLineNumberAndItemNumber() {
    LabelSequence labelSequence =
        labelSequenceRepository
            .findByMustArriveBeforeDateAndPurchaseReferenceLineNumberAndItemNumberAndLabelType(
                mustArriveBeforeDate, 1, 876987l, LabelType.ORDERED);
    assertEquals(mockLabelSequenceList.get(0).getItemNumber(), labelSequence.getItemNumber());
    assertEquals(
        mockLabelSequenceList.get(0).getPurchaseReferenceLineNumber(),
        labelSequence.getPurchaseReferenceLineNumber());
    assertEquals(
        mockLabelSequenceList.get(0).getNextSequenceNo(), labelSequence.getNextSequenceNo());
    assertEquals(mockLabelSequenceList.get(0).getLabelType(), labelSequence.getLabelType());
    assertNotNull(labelSequence.getMustArriveBeforeDate());
    assertNotNull(labelSequence.getCreateTs());
  }
}
