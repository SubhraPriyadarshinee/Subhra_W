package com.walmart.move.nim.receiving.fixture.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.fixture.model.PalletItem;
import com.walmart.move.nim.receiving.fixture.model.PalletMapLPNRequest;
import com.walmart.move.nim.receiving.fixture.model.PalletPutAwayRequest;
import com.walmart.move.nim.receiving.fixture.model.PalletReceiveRequest;
import com.walmart.move.nim.receiving.fixture.model.PalletReceiveResponse;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import java.util.Collections;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FixturePalletControllerTest extends ReceivingControllerTestBase {
  private MockMvc mockMvc;
  private FixturePalletController fixturePalletController;
  @Mock private PalletReceivingService palletReceivingService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    fixturePalletController = new FixturePalletController();

    ReflectionTestUtils.setField(
        fixturePalletController, "palletReceivingService", palletReceivingService);
    this.mockMvc = MockMvcBuilders.standaloneSetup(fixturePalletController).build();
  }

  @AfterMethod
  public void clear() {
    reset(palletReceivingService);
  }

  @Test
  public void testReceiveSuccess() throws Exception {

    when(palletReceivingService.receive(any(), any())).thenReturn(getPalletReceiveResponse());
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "  \"packNumber\": \"B32899000020011086\"" + "}"))
        .andExpect(status().isOk());

    ArgumentCaptor<PalletReceiveRequest> captor =
        ArgumentCaptor.forClass(PalletReceiveRequest.class);
    verify(palletReceivingService, times(1)).receive(captor.capture(), any());
    PalletReceiveRequest palletReceiveRequest = captor.getValue();
    assertNotNull(palletReceiveRequest);
    assertEquals(palletReceiveRequest.getPackNumber(), "B32899000020011086");
    assertNull(palletReceiveRequest.getItems());
  }

  @Test
  public void testReceiveSuccess2() throws Exception {

    when(palletReceivingService.receive(any(), any())).thenReturn(getPalletReceiveResponse());
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "  \"packNumber\": \"B32899000020011086\",\n"
                        + "  \"items\": [\n"
                        + "    {\n"
                        + "      \"itemNumber\": 667232180,\n"
                        + "      \"receivedQty\": 4\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}"))
        .andExpect(status().isOk());

    ArgumentCaptor<PalletReceiveRequest> captor =
        ArgumentCaptor.forClass(PalletReceiveRequest.class);
    verify(palletReceivingService, times(1)).receive(captor.capture(), any());
    PalletReceiveRequest palletReceiveRequest = captor.getValue();
    assertNotNull(palletReceiveRequest);
    assertEquals(palletReceiveRequest.getPackNumber(), "B32899000020011086");
    assertNotNull(palletReceiveRequest.getItems());
    assertEquals(palletReceiveRequest.getItems().size(), 1);
    assertEquals(palletReceiveRequest.getItems().get(0).getItemNumber(), Long.valueOf(667232180));
    assertEquals(palletReceiveRequest.getItems().get(0).getReceivedQty(), Integer.valueOf(4));
  }

  @Test
  public void testReceiveBadRequest2() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "  \"packNumber\": \"4473A18266111\",\n"
                        + "  \"items\": [\n"
                        + "    {\n"
                        + "      \"receivedQty\": 4\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}"))
        .andExpect(status().isBadRequest());
  }

  private PalletReceiveResponse getPalletReceiveResponse() {
    PalletItem palletItem =
        PalletItem.builder()
            .itemNumber(11111L)
            .itemDescription("Part")
            .receivedQty(5)
            .orderedQty(5)
            .purchaseReferenceNumber("2356789123")
            .purchaseReferenceLineNumber(1)
            .quantityUOM("EA")
            .build();

    PalletReceiveResponse palletReceiveResponse =
        PalletReceiveResponse.builder()
            .packNumber("B32899000020011086")
            .items(Collections.singletonList(palletItem))
            .build();

    return palletReceiveResponse;
  }

  @Test
  public void testPutAwaySuccess() throws Exception {

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/putaway")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "    \"lpn\": \"LPN 10574 INV 4003\",\n"
                        + "    \"location\": \"F1-034\"\n"
                        + "}"))
        .andExpect(status().isOk());

    ArgumentCaptor<PalletPutAwayRequest> captor =
        ArgumentCaptor.forClass(PalletPutAwayRequest.class);
    verify(palletReceivingService, times(1)).putAway(captor.capture(), any());
    PalletPutAwayRequest palletReceiveRequest = captor.getValue();
    assertNotNull(palletReceiveRequest);
    assertEquals(palletReceiveRequest.getLpn(), "LPN 10574 INV 4003");
    assertEquals(palletReceiveRequest.getLocation(), "F1-034");
  }

  @Test
  public void testPutAwayBadRequest() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/putaway")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "    \"lpn\": \"LPN 10574 INV 4003\"" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testPutAwayBadRequest2() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/putaway")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "    \"location\": \"F1-034\"\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testMapLpnSuccess() throws Exception {

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/lpn")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "{\n"
                        + "  \"packNumber\": \"4473A18266111\",\n"
                        + "  \"lpn\": \"LPN 10656 INV 4784\"\n"
                        + "}"))
        .andExpect(status().isOk());

    ArgumentCaptor<PalletMapLPNRequest> captor = ArgumentCaptor.forClass(PalletMapLPNRequest.class);
    verify(palletReceivingService, times(1)).mapLpn(captor.capture(), any());
    PalletMapLPNRequest palletReceiveRequest = captor.getValue();
    assertNotNull(palletReceiveRequest);
    assertEquals(palletReceiveRequest.getLpn(), "LPN 10656 INV 4784");
    assertEquals(palletReceiveRequest.getPackNumber(), "4473A18266111");
  }

  @Test
  public void testMapLpnBadRequest() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/lpn")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "    \"lpn\": \"LPN 10574 INV 4003\"" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testMapLpnBadRequest2() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/lpn")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "  \"packNumber\": \"4473A18266111\",\n" + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testReceiveV2Success() throws Exception {

    when(palletReceivingService.receive(any(), any())).thenReturn(getPalletReceiveResponse());
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/pallet/v2/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "  \"packNumber\": \"B32899000020011086\"" + "}"))
        .andExpect(status().isOk());

    ArgumentCaptor<PalletReceiveRequest> captor =
        ArgumentCaptor.forClass(PalletReceiveRequest.class);
    verify(palletReceivingService, times(1)).receiveV2(captor.capture(), any());
    PalletReceiveRequest palletReceiveRequest = captor.getValue();
    assertNotNull(palletReceiveRequest);
    assertEquals(palletReceiveRequest.getPackNumber(), "B32899000020011086");
    assertNull(palletReceiveRequest.getItems());
  }
}
