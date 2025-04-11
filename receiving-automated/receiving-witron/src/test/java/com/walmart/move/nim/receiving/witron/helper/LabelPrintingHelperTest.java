package com.walmart.move.nim.receiving.witron.helper;

import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.GDC_LABEL_FORMAT_NAME;
import static com.walmart.move.nim.receiving.witron.mock.data.MockInstruction.getManualGdcContainerData;
import static java.lang.Integer.valueOf;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LabelPrintingHelperTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private GDCFlagReader gdcFlagReader;

  @InjectMocks private LabelPrintingHelper labelPrintingHelper;

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private final String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);

  private ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();

  private Instruction instruction = MockInstruction.getInstruction();

  private HttpHeaders mockHeaders = GdcHttpHeaders.getHeaders();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(valueOf(facilityNum));
  }

  @BeforeMethod
  public void setUpTestData() {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("ZA");
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(gdcFlagReader);
  }

  @Test
  public void testGetLabelDataV1() {
    when(tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum()))
        .thenReturn("UTC");
    when(gdcFlagReader.isGdcLabelV2Enabled()).thenReturn(false);

    Map<String, Object> printJob =
        labelPrintingHelper.getLabelData(instruction, receiveInstructionRequest, mockHeaders);

    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(printRequest.get("formatName"), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);
  }

  @Test
  public void testGetLabelDataV2() {
    when(tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum()))
        .thenReturn("UTC");
    when(gdcFlagReader.isGdcLabelV2Enabled()).thenReturn(true);

    instruction.setContainer(getManualGdcContainerData());

    Map<String, Object> printJob =
        labelPrintingHelper.getLabelData(instruction, receiveInstructionRequest, mockHeaders);

    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "TAG-123");
    assertEquals(printRequest.get("formatName"), GDC_LABEL_FORMAT_NAME);
  }
}
