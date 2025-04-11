package com.walmart.move.nim.receiving.fixture.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.fixture.model.ItemDTO;
import com.walmart.move.nim.receiving.fixture.service.FixtureItemService;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

public class FixtureItemControllerTest extends ReceivingTestBase {

  @InjectMocks private FixtureItemController fixtureItemController;
  @Mock private FixtureItemService fixtureItemService;

  private MockMvc mockMvc;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(fixtureItemController, "fixtureItemService", fixtureItemService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(fixtureItemController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
  }

  @AfterMethod
  public void clear() {
    reset(fixtureItemService);
  }

  @Test
  public void testGetAllItemsNoContent() throws Exception {
    when(fixtureItemService.findAllItems(any(), any(), any())).thenReturn(Arrays.asList());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/fixture/item/")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isNoContent());

    verify(fixtureItemService, times(1)).findAllItems(null, null, null);
  }

  @Test
  public void testGetAllItems() throws Exception {
    ItemDTO item = ItemDTO.builder().itemNumber(111L).description("pen").build();
    when(fixtureItemService.findAllItems(any(), any(), any())).thenReturn(Arrays.asList(item));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/fixture/item/")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());

    verify(fixtureItemService, times(1)).findAllItems(null, null, null);
  }

  @Test
  public void testGetAllItemsSearchString() throws Exception {
    ItemDTO item = ItemDTO.builder().itemNumber(111L).description("pen").build();
    when(fixtureItemService.findAllItems(any(), anyInt(), anyInt()))
        .thenReturn(Arrays.asList(item));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/fixture/item?searchString=print&page=0&size=100")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());

    verify(fixtureItemService, times(1)).findAllItems("print", 0, 100);
  }

  @Test
  public void testAddItems() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/fixture/item")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content(
                    "[\n"
                        + "  {\n"
                        + "    \"itemNumber\": 100667111,\n"
                        + "    \"description\": \"WM 095368 NLC 1H21 14X78 Set\"\n"
                        + "  }\n"
                        + "]"))
        .andExpect(status().isOk());
    ArgumentCaptor<List<ItemDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(fixtureItemService, times(1)).addItems(captor.capture());
    List<ItemDTO> list = captor.getValue();
    assertNotNull(list);
    assertEquals(list.size(), 1);
    assertEquals(list.get(0).getDescription(), "WM 095368 NLC 1H21 14X78 Set");
    assertEquals(list.get(0).getItemNumber(), Long.valueOf(100667111L));
  }
}
