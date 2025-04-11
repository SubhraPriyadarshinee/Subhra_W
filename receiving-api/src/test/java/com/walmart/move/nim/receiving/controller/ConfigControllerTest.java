package com.walmart.move.nim.receiving.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.config.AtlasUiConfig;
import com.walmart.move.nim.receiving.core.config.AtlasUiConstants;
import com.walmart.move.nim.receiving.core.config.UserOverridenClientConfig;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ConfigControllerTest extends ReceivingControllerTestBase {

  private MockMvc mockMvc;
  @Mock private AtlasUiConstants uiConstants;
  @Mock private AtlasUiConfig uiConfig;
  @Mock private UserOverridenClientConfig userOverridenClientConfig;

  @InjectMocks private ConfigController configController;

  @BeforeClass
  public void initMocks() {
    ReflectionTestUtils.setField(configController, "uiConfig", uiConfig);
    ReflectionTestUtils.setField(
        configController, "userOverridenClientConfig", userOverridenClientConfig);
    mockMvc = MockMvcBuilders.standaloneSetup(configController).build();
  }

  @Test
  public void testGetClientConstants() {
    try {
      HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
      ReflectionTestUtils.setField(configController, "uiConstants", uiConstants);

      File resource = new ClassPathResource("test_clientconstants.json").getFile();
      String clientConstants = new String(Files.readAllBytes(resource.toPath()));

      when(uiConstants.getConstants()).thenReturn(clientConstants);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.get("/configuration/clientconstants")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertTrue(response.contains("R10"));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetClientConstants_Default() {
    try {
      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32611");
      httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
      httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "witronTest");
      httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "3a2b6c1d2e");
      httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "1");
      httpHeaders.add(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

      ReflectionTestUtils.setField(configController, "uiConstants", uiConstants);

      File resource = new ClassPathResource("test_clientconstants.json").getFile();
      String clientConstants = new String(Files.readAllBytes(resource.toPath()));

      when(uiConstants.getConstants()).thenReturn(clientConstants);

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.get("/configuration/clientconstants")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertFalse(response.contains("R10"));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetClientConfig() {
    try {
      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

      File resource = new ClassPathResource("test_clientConfig.json").getFile();
      String clientConfig = new String(Files.readAllBytes(resource.toPath()));

      when(uiConfig.getFeatureFlags()).thenReturn(clientConfig);
      when(userOverridenClientConfig.getUsers()).thenReturn(new ArrayList<>());

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.get("/configuration/clientconfig")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertTrue(response.contains("isAtlas"));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetClientConfigGetDefault() {
    try {
      HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
      httpHeaders.remove(ReceivingConstants.TENENT_FACLITYNUM);
      httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");

      File resource = new ClassPathResource("test_clientConfig.json").getFile();
      String clientConfig = new String(Files.readAllBytes(resource.toPath()));

      when(uiConfig.getFeatureFlags()).thenReturn(clientConfig);
      when(userOverridenClientConfig.getUsers()).thenReturn(new ArrayList<>());

      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.get("/configuration/clientconfig")
                      .contentType(MediaType.APPLICATION_JSON)
                      .headers(httpHeaders))
              .andExpect(status().is2xxSuccessful())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertFalse(response.contains("isAtlas"));
    } catch (Exception e) {
      assertTrue(false);
    }
  }
}
