package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.LabelMetaData;
import com.walmart.move.nim.receiving.core.mock.data.MockLabelMetaData;
import com.walmart.move.nim.receiving.core.repositories.LabelMetaDataRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelPersisterServiceTest extends ReceivingTestBase {
  @Autowired LabelMetaDataRepository labelMetaDataRepository;
  @InjectMocks LabelPersisterService labelPersisterService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        labelPersisterService, "labelMetaDataRepository", labelMetaDataRepository);
    labelMetaDataRepository.saveAll(MockLabelMetaData.getLabelMetaData());
  }

  @Test
  public void getLabelMetaData_findAll() {
    Set<Integer> labelIds = new HashSet<>();
    labelIds.add(101);
    labelIds.add(102);
    List<LabelMetaData> labelMetaDataList =
        labelPersisterService.getLabelMetaDataByLabelIdsIn(labelIds);
    assertEquals(2, labelMetaDataList.size());
  }
}
