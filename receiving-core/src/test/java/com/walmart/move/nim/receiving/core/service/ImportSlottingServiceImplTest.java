package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ImportSlottingServiceImplTest {
  @Mock SlottingRestApiClient slottingRestApiClient;
  @InjectMocks ImportSlottingServiceImpl importSlottingServiceImpl;

  @BeforeMethod
  public void createImportSlottingServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32888);
  }

  @Test
  public void testGetPrimeSlot_Success() {
    HttpHeaders httpHeaders = MockHttpHeaders.getImportHeaders();
    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);
    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);
    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));
    SlottingPalletResponse getPrimeslot =
        importSlottingServiceImpl.getPrimeSlot(
            "a1-b1-c1", Arrays.asList(Long.valueOf(123456)), 0, httpHeaders);
    assertNotNull(getPrimeslot);
    assertTrue(StringUtils.isNotBlank(getPrimeslot.getMessageId()));
    assertEquals(getPrimeslot.getLocations().get(0).getLocation(), "A1234");
  }

  @Test
  public void testGetPrimeSlot_notfound() {
    HttpHeaders httpHeaders = MockHttpHeaders.getImportHeaders();
    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("error");
    location.setCode("GLS-SMART-SLOTING-4040009");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);
    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);
    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));
    SlottingPalletResponse getPrimeslot =
        importSlottingServiceImpl.getPrimeSlot(
            "a1-b1-c1", Arrays.asList(Long.valueOf(123456)), 0, httpHeaders);
    assertNotNull(getPrimeslot);
    assertTrue(StringUtils.isNotBlank(getPrimeslot.getMessageId()));
    assertEquals(getPrimeslot.getLocations().get(0).getCode(), "GLS-SMART-SLOTING-4040009");
  }
}
