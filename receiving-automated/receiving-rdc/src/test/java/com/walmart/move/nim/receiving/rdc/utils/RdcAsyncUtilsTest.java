package com.walmart.move.nim.receiving.rdc.utils;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcAsyncUtilsTest {

  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks RdcAsyncUtils rdcAsyncUtils;
  private HttpHeaders httpHeaders;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
  }

  @BeforeMethod
  public void setup() {
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, facilityCountryCode);
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");
  }

  @AfterMethod
  public void resetMocks() {
    reset(hawkeyeRestApiClient, tenantSpecificConfigReader);
  }

  @Test
  public void testUpdateLabelStatusVoidToHawkeye() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<String> trackingIdList = Arrays.asList("32327623676");
    doNothing().when(hawkeyeRestApiClient).labelUpdateToHawkeye(any(), any());
    rdcAsyncUtils.updateLabelStatusVoidToHawkeye(trackingIdList, httpHeaders);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    verify(hawkeyeRestApiClient, times(1)).labelUpdateToHawkeye(any(), any());
  }

  @Test
  public void testLabelUpdateToHawkeyeForDownloadedStatus_Success() {
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData1 = new LabelData();
    labelData1.setDeliveryNumber(232223L);
    labelData1.setPurchaseReferenceNumber("23232323");
    labelData1.setItemNumber(9872323L);
    labelDataList.add(labelData1);
    doNothing().when(hawkeyeRestApiClient).labelUpdateToHawkeye(any(), any());
    rdcAsyncUtils.labelUpdateToHawkeye(httpHeaders, labelDataList);
    verify(hawkeyeRestApiClient, times(1)).labelUpdateToHawkeye(any(), any());
  }

  @Test
  public void testLabelUpdateToHawkeyeForDownloadedStatus_Exception() {
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData1 = new LabelData();
    labelData1.setDeliveryNumber(232223L);
    labelData1.setPurchaseReferenceNumber("23232323");
    labelData1.setItemNumber(9872323L);
    labelDataList.add(labelData1);
    doThrow(new ReceivingInternalException("error", "error"))
        .when(hawkeyeRestApiClient)
        .labelUpdateToHawkeye(any(), any());
    rdcAsyncUtils.labelUpdateToHawkeye(httpHeaders, labelDataList);
    verify(hawkeyeRestApiClient, times(1)).labelUpdateToHawkeye(any(), any());
  }

  @Test
  public void testUpdateLabelStatusVoidToHawkeyeThrowException() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<String> trackingIdList = Arrays.asList("32327623676");
    doThrow(new ReceivingInternalException("error", "error"))
        .when(hawkeyeRestApiClient)
        .labelUpdateToHawkeye(any(), any());
    rdcAsyncUtils.updateLabelStatusVoidToHawkeye(trackingIdList, httpHeaders);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    verify(hawkeyeRestApiClient, times(1)).labelUpdateToHawkeye(any(), any());
  }
}
