package com.walmart.move.nim.receiving.endgame.repositories;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.LabelSummary;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PreLabelDataRepositoryTest extends ReceivingTestBase {

  @Autowired private PreLabelDataCustomRepository preLabelDataCustomRepository;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  @Autowired private EndgameOutboxHandler endGameOutboxHandler;

  @BeforeClass
  public void setRootUp() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    ReflectionTestUtils.setField(
        endGameOutboxHandler, "iOutboxPublisherService", iOutboxPublisherService);
  }

  @Test
  private void findLabelSummaryTest() {
    List<LabelSummary> resultList =
        preLabelDataCustomRepository.findLabelSummary(12345678L, EndgameConstants.ALL);
    Assert.assertEquals(resultList.size(), 0);
  }

  @Test
  private void findLabelSummaryWithLabelTest() {
    List<LabelSummary> resultList = preLabelDataCustomRepository.findLabelSummary(12345678L, "TPL");
    Assert.assertEquals(resultList.size(), 0);
  }
}
