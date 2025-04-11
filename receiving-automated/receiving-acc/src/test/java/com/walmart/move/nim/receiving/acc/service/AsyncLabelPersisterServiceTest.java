package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.service.PrintingAndLabellingService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncLabelPersisterServiceTest extends ReceivingTestBase {
  @InjectMocks private AsyncLabelPersisterService asyncLabelPersisterService;

  @InjectMocks private GenericLabelingService labelingService;

  @Mock private AppConfig appConfig;

  @Mock private PrintingAndLabellingService printingAndLabellingService;

  @Mock private LabelingHelperService labelingHelperService;

  private HttpHeaders httpHeaders;

  private DeliveryDetails deliveryDetails;

  private List<LabelData> labelDataList;

  @BeforeClass
  public void initMocks() throws ReceivingException {
    MockitoAnnotations.initMocks(this);
    httpHeaders = MockHttpHeaders.getHeaders();
    ReflectionTestUtils.setField(asyncLabelPersisterService, "labelingService", labelingService);
    ReflectionTestUtils.setField(
        asyncLabelPersisterService, "printingAndLabellingService", printingAndLabellingService);
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsMultiPOPOL.json")
              .getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
      dataPath =
          new File("../../receiving-test/src/main/resources/json/LabelDataMultiPOPOL.json")
              .getCanonicalPath();
      labelDataList =
          Arrays.asList(
              JacksonParser.convertJsonToObject(
                  new String(Files.readAllBytes(Paths.get(dataPath))), LabelData[].class));
    } catch (IOException e) {
      assert (false);
    }
  }

  @Test
  public void testPublishLabelDataToLabelling_happyPath() {
    doNothing().when(printingAndLabellingService).postToLabelling(any(), any(HttpHeaders.class));
    doReturn(10).when(appConfig).getLabellingServiceCallBatchCount();
    doReturn(72).when(appConfig).getLabelTTLInHours();
    doReturn("case_lpn_format").when(appConfig).getPreLabelFormatName();
    asyncLabelPersisterService.publishLabelDataToLabelling(
        deliveryDetails, labelDataList, httpHeaders);
    verify(printingAndLabellingService, times(8)).postToLabelling(any(), any(HttpHeaders.class));
  }
}
