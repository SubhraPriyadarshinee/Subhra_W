package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultKafkaInstructionDownloadProcessorTest {

  @InjectMocks
  private DefaultKafkaInstructionDownloadProcessor defaultKafkaInstructionDownloadProcessor;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void azureBlobConfigTest() {
    MessageData messageData = new MessageData();
    messageData.setFacilityNum(32769);
    messageData.setFacilityCountryCode("US");
    defaultKafkaInstructionDownloadProcessor.processEvent(messageData);
  }
}
