package com.walmart.move.nim.receiving.controller;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class VersionCheckControllerTest {

  @InjectMocks private VersionCheckController versionCheckController;
  @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;
  private MockMvc mockMvc;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  @Mock private ServletContext servletContext;

  private Gson gson = new Gson();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    versionCheckController = new VersionCheckController();
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(versionCheckController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetAppVersion() {
    try {

      doReturn(new ByteArrayInputStream("a=b".getBytes(StandardCharsets.UTF_8)))
          .when(servletContext)
          .getResourceAsStream("/META-INF/MANIFEST.MF");
      mockMvc
          .perform(MockMvcRequestBuilders.get("/version").headers(MockHttpHeaders.getHeaders()))
          .andExpect(status().isOk());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}
