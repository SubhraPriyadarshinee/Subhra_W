package com.walmart.move.nim.receiving.core.repositories;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByProblemIdResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ReceiptCustomRepositoryTest {

  @InjectMocks ReceiptCustomRepository receiptCustomRepository;
  @Mock private EntityManager entityManager;
  @Mock private Query query;
  Long deliveryNumber = 123445L;
  List<ReceiptSummaryVnpkResponse> receipts = new ArrayList<>();

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @BeforeMethod
  public void resetMocks() {
    reset(entityManager);
    reset(query);
  }

  @Test
  public void testReceiptSummaryByDelivery() {
    ReceiptSummaryVnpkResponse receipt =
        new ReceiptSummaryVnpkResponse("1467778615", 1, "ZA", 30L, 20L);
    receipts.add(receipt);
    when(entityManager.createNamedQuery("Receipt.receiptSummaryByDelivery")).thenReturn(query);
    when(query.setParameter("deliveryNumber", deliveryNumber)).thenReturn(query);
    when(query.getResultList()).thenReturn(receipts);
    List<ReceiptSummaryVnpkResponse> receiptsSummaryResponse =
        receiptCustomRepository.receiptSummaryByDelivery(deliveryNumber);
    assertNotNull(receiptsSummaryResponse);
    assertEquals(receiptsSummaryResponse.size(), 1);
  }

  @Test
  public void testReceiptSummaryByDeliveryNoResult() {
    when(entityManager.createNamedQuery("Receipt.receiptSummaryByDelivery")).thenReturn(query);
    when(query.setParameter("deliveryNumber", deliveryNumber)).thenReturn(query);
    doThrow(NoResultException.class).when(query).getResultList();
    List<ReceiptSummaryVnpkResponse> receiptsSummaryResponse =
        receiptCustomRepository.receiptSummaryByDelivery(deliveryNumber);
    assertEquals(receiptsSummaryResponse.size(), 0);
  }

  @Test
  public void test_getReceivedQtyInVnpk() {
    String problemId = "12";
    long receivedQtyByProblemId = 20L;
    final ReceiptSummaryQtyByProblemIdResponse mockResponse =
        new ReceiptSummaryQtyByProblemIdResponse(problemId, receivedQtyByProblemId);
    List<ReceiptSummaryQtyByProblemIdResponse> responseList = new ArrayList<>();
    responseList.add(mockResponse);
    when(entityManager.createNamedQuery("Receipt.receivedQtyByProblemIdInVnpk")).thenReturn(query);
    when(query.setParameter("problemId", problemId)).thenReturn(query);
    when(query.setParameter(TENENT_FACLITYNUM, getFacilityNum())).thenReturn(query);
    when(query.setParameter(TENENT_COUNTRY_CODE, getFacilityCountryCode())).thenReturn(query);
    when(query.getSingleResult()).thenReturn(mockResponse);
    final ReceiptSummaryQtyByProblemIdResponse response =
        receiptCustomRepository.receivedQtyByProblemIdInVnpk(problemId);
    assertNotNull(response);
    assertEquals(response.getProblemId(), problemId);
    assertEquals(response.getReceivedQty().longValue(), receivedQtyByProblemId);
    assertEquals(response.getQtyUOM(), VNPK);
  }

  @Test
  public void testRcvdPackCountByDelivery() {
    ReceiptSummaryVnpkResponse receipt = new ReceiptSummaryVnpkResponse("1467778615", 2);
    when(entityManager.createNamedQuery("Receipt.receivedPacksByDeliveryAndPo")).thenReturn(query);
    when(query.setParameter("deliveryNumber", deliveryNumber)).thenReturn(query);
    when(query.getResultList()).thenReturn(Collections.singletonList(receipt));
    List<ReceiptSummaryVnpkResponse> receiptsSummaryResponse =
        receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(deliveryNumber);
    assertNotNull(receiptsSummaryResponse);
    assertEquals(receiptsSummaryResponse.size(), 1);
  }

  @Test
  public void testRcvdPackCountByDeliveryNoResult() {
    when(entityManager.createNamedQuery("Receipt.receivedPacksByDeliveryAndPo")).thenReturn(query);
    when(query.setParameter("deliveryNumber", deliveryNumber)).thenReturn(query);
    doThrow(NoResultException.class).when(query).getResultList();
    List<ReceiptSummaryVnpkResponse> receiptsSummaryResponse =
        receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(deliveryNumber);
    assertEquals(receiptsSummaryResponse.size(), 0);
  }
}
