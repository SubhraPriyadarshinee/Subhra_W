package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.service.PurgeService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import io.strati.security.jaxrs.ws.rs.core.MediaType;
import java.util.Collections;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PurgeControllerTest extends ReceivingControllerTestBase {

  @Mock private PurgeService purgeService;
  private MockMvc mockMvc;
  PurgeController purgeController;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    purgeController = new PurgeController();
    ReflectionTestUtils.setField(purgeController, "purgeService", purgeService);
    this.mockMvc = MockMvcBuilders.standaloneSetup(purgeController).build();
  }

  @AfterMethod
  public void clear() {
    reset(purgeService);
  }

  @Test
  public void testOnBoardEntity() throws Exception {
    PurgeData purgeData = PurgeData.builder().purgeEntityType(PurgeEntityType.INSTRUCTION).build();
    when(purgeService.createEntities(anyList())).thenReturn(Collections.singletonList(purgeData));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/purge/onboard")
                .content(MediaType.APPLICATION_JSON)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "  \"entities\":[\"INSTRUCTION\"]\n" + "}"))
        .andExpect(status().isCreated());

    verify(purgeService, times(1)).createEntities(anyList());
  }

  @Test
  public void testOnBoardEntityInvalidEntity() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/purge/onboard")
                .content(MediaType.APPLICATION_JSON)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders())
                .content("{\n" + "  \"entities\":[\"instruction\"]\n" + "}"))
        .andExpect(status().isBadRequest());

    verify(purgeService, times(0)).createEntities(anyList());
  }
}
