package com.walmart.move.nim.receiving.rdc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.service.RdcContainerService;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcContainerControllerTest {

  @InjectMocks private RdcContainerController rdcContainerController;
  @Mock private RdcContainerService rdcContainerService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private RdcContainerUtils rdcContainerUtils;
  private MockMvc mockMvc;
  private static final String GET_CONTAINER_BY_UPC = "/rdc/containers/upc/";
  private HttpHeaders httpHeaders;
  private Gson gson = new Gson();

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rdcContainerController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
    httpHeaders = MockHttpHeaders.getHeaders();
  }

  @AfterMethod
  public void tearDown() {
    reset(rdcContainerService, containerPersisterService, rdcContainerUtils);
  }

  @Test
  private void testGetContainersByUpcMissingUPCNumber() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get(GET_CONTAINER_BY_UPC).headers(httpHeaders))
        .andExpect(MockMvcResultMatchers.status().isNotFound());
    verify(rdcContainerService, times(0))
        .getContainerItemsByUpc(anyString(), any(HttpHeaders.class));
  }

  @Test
  private void testGetContainersByUpcSuccess() throws Exception {
    when(rdcContainerService.getContainerItemsByUpc(anyString(), any(HttpHeaders.class)))
        .thenReturn(getMockContainerItems());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(GET_CONTAINER_BY_UPC + "0808232323442").headers(httpHeaders))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

    verify(rdcContainerService, times(1))
        .getContainerItemsByUpc(anyString(), any(HttpHeaders.class));
  }

  private List<ContainerItem> getMockContainerItems() {
    List<ContainerItem> containerItemList = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("9078574757");
    containerItem.setItemNumber(34343434L);
    containerItem.setItemUPC("0007334343434");
    containerItemList.add(containerItem);
    return containerItemList;
  }
}
