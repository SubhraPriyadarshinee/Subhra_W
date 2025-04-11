package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.entity.LabelSequence;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.repositories.LabelSequenceRepository;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelSequenceServiceTest {
  @InjectMocks private LabelSequenceService labelSequenceService;
  @Mock private LabelSequenceRepository labelSequenceRepository;

  private final Date mustArriveBeforeDate = new Date();
  private List<LabelSequence> mockLabelSequenceList;

  @BeforeClass
  private void initMocks() {
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
            .purchaseReferenceLineNumber(1)
            .mustArriveBeforeDate(mustArriveBeforeDate)
            .labelType(LabelType.OVERAGE)
            .build();
    mockLabelSequenceList = Arrays.asList(labelSequence1, labelSequence2);
  }

  @AfterMethod
  private void resetMocks() {
    reset(labelSequenceRepository);
  }

  @Test
  public void testFindByMABDPOLineNumberItemNumber() {
    when(labelSequenceRepository
            .findByMustArriveBeforeDateAndPurchaseReferenceLineNumberAndItemNumberAndLabelType(
                any(Date.class), anyInt(), anyLong(), any(LabelType.class)))
        .thenReturn(mockLabelSequenceList.get(0));
    LabelSequence labelSequence =
        labelSequenceService.findByMABDPOLineNumberItemNumberLabelType(
            mustArriveBeforeDate, 1, 876987l, LabelType.ORDERED);
    assertNotNull(labelSequence);
    verify(labelSequenceRepository, times(1))
        .findByMustArriveBeforeDateAndPurchaseReferenceLineNumberAndItemNumberAndLabelType(
            any(Date.class), anyInt(), anyLong(), any(LabelType.class));
  }

  @Test
  public void testSaveLabel() {
    when(labelSequenceRepository.save(any())).thenReturn(mockLabelSequenceList.get(0));
    labelSequenceService.save(mockLabelSequenceList.get(0));
    verify(labelSequenceRepository, times(1)).save(any(LabelSequence.class));
  }
}
