package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.core.filter.MTFilter;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SpringBootTest
@ActiveProfiles("test")
public class TestMTFilter {

  @Mock HttpServletRequest httpServletRequest;

  @Mock HttpServletResponse httpServletResponse;

  @Mock FilterChain filterChain;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(httpServletRequest);
    reset(httpServletResponse);
    reset(filterChain);
  }

  @Test
  public void testHeaderNotPresent() throws IOException, ServletException {
    when(httpServletRequest.getRequestURI()).thenReturn("/receipts/delivery/234/summary/");
    // httpServletResponse.setStatus(200);
    MTFilter filterUnderTest = new MTFilter();

    filterUnderTest.doFilter(httpServletRequest, httpServletResponse, filterChain);
    verify(httpServletResponse)
        .sendError(HttpStatus.BAD_REQUEST.value(), ReceivingConstants.INVALID_TENENT_INFO);
    // assertEquals(httpServletResponse.getStatus() , 400);

  }

  @Test
  public void testHeaderPresent() throws IOException, ServletException {

    when(httpServletRequest.getRequestURI()).thenReturn("/receipts/delivery/234/summary/");
    when(httpServletRequest.getHeader("faclityNum")).thenReturn("6098");
    when(httpServletRequest.getHeader("faclityCountryCode")).thenReturn("us");

    MTFilter filterUnderTest = new MTFilter();
    filterUnderTest.doFilter(httpServletRequest, httpServletResponse, filterChain);
    assertEquals(httpServletResponse.getStatus(), 0);
  }

  @Test
  public void testHeaderPresent_shouldClearMdcContext() throws IOException, ServletException {

    when(httpServletRequest.getRequestURI()).thenReturn("/receipts/delivery/234/summary/");
    when(httpServletRequest.getHeader("faclityNum")).thenReturn("6098");
    when(httpServletRequest.getHeader("faclityCountryCode")).thenReturn("us");

    MTFilter filterUnderTest = new MTFilter();
    filterUnderTest.doFilter(httpServletRequest, httpServletResponse, filterChain);
    assertNull(MDC.get("faclityNum"));
  }

  @Test
  public void testRemoveHostHeader() throws IOException, ServletException {
    when(httpServletRequest.getRequestURI()).thenReturn("/receipts/delivery/234/summary/");
    when(httpServletRequest.getHeader("facilityNum")).thenReturn("6098");
    when(httpServletRequest.getHeader("facilityCountryCode")).thenReturn("us");
    when(httpServletRequest.getHeader("WMT-UserId")).thenReturn("dummy-user");
    when(httpServletRequest.getHeader("host")).thenReturn("dummy-host");
    when(httpServletRequest.getHeader(ReceivingConstants.SUBCENTER_ID_HEADER)).thenReturn("1");
    ArgumentCaptor<HttpServletRequest> httpServletRequestArgumentCaptor =
        ArgumentCaptor.forClass(HttpServletRequest.class);
    MTFilter filterUnderTest = new MTFilter();
    filterUnderTest.doFilter(httpServletRequest, httpServletResponse, filterChain);
    verify(filterChain, times(1)).doFilter(httpServletRequestArgumentCaptor.capture(), any());
    assertNull(httpServletRequestArgumentCaptor.getValue().getHeader("host"));
  }
}
