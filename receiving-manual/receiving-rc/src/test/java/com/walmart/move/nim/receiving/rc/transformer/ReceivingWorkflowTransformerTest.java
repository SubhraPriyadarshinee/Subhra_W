package com.walmart.move.nim.receiving.rc.transformer;

import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.contants.WorkflowStatus;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowCreateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.ReceiveContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowResponse;
import java.util.Collections;
import java.util.Date;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReceivingWorkflowTransformerTest {
  ReceivingWorkflowTransformer receivingWorkflowTransformer;
  ReceivingWorkflow receivingWorkflow;
  ReceiveContainerRequest receiveContainerRequest;

  @BeforeClass
  public void initMocksAndFields() {
    receivingWorkflowTransformer = new ReceivingWorkflowTransformer();
    receivingWorkflow =
        ReceivingWorkflow.builder()
            .id(10001L)
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .imageCount(0)
            .imageComment("")
            .createTs(new Date())
            .createUser("m0s0mqs")
            .lastChangedTs(new Date())
            .lastChangedUser("m0s0mqs")
            .packageBarcodeValue("200014189234108")
            .status(WorkflowStatus.IN_PROGRESS)
            .type(WorkflowType.FRAUD)
            .workflowItems(
                Collections.singletonList(
                    ReceivingWorkflowItem.builder()
                        .action(WorkflowAction.FRAUD)
                        .gtin("00012123123")
                        .id(1L)
                        .createTs(new Date())
                        .createUser("m0s0mqs")
                        .itemTrackingId("12121L")
                        .lastChangedTs(new Date())
                        .lastChangedUser("m0s0mqs")
                        .build()))
            .build();

    receiveContainerRequest =
        ReceiveContainerRequest.builder()
            .scannedLabel("1000100001")
            .scannedItemLabel("000001212312")
            .workflowCreateReason("ITEM_MISSING")
            .build();
  }

  @Test
  public void testTransformWorkflowEntityToDTO() {
    RcWorkflowResponse response =
        receivingWorkflowTransformer.transformWorkflowEntityToDTO(receivingWorkflow);

    // validate workflow fields
    Assert.assertEquals(response.getId(), receivingWorkflow.getId());
    Assert.assertEquals(response.getWorkflowId(), receivingWorkflow.getWorkflowId());
    Assert.assertEquals(response.getCreateReason(), receivingWorkflow.getCreateReason());
    Assert.assertEquals(response.getCreateTs(), receivingWorkflow.getCreateTs());
    Assert.assertEquals(response.getCreateUser(), receivingWorkflow.getCreateUser());
    Assert.assertEquals(response.getLastChangedTs(), receivingWorkflow.getLastChangedTs());
    Assert.assertEquals(response.getLastChangedUser(), receivingWorkflow.getLastChangedUser());
    Assert.assertEquals(
        response.getPackageBarcodeValue(), receivingWorkflow.getPackageBarcodeValue());
    Assert.assertEquals(
        response.getPackageBarcodeType(), receivingWorkflow.getPackageBarcodeType());
    Assert.assertEquals(response.getType(), receivingWorkflow.getType().name());
    Assert.assertEquals(
        response.getWorkflowItems().size(), receivingWorkflow.getWorkflowItems().size());

    // validate workflow item fields
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getId(),
        receivingWorkflow.getWorkflowItems().get(0).getId());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getAction(),
        receivingWorkflow.getWorkflowItems().get(0).getAction().name());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getCreateTs(),
        receivingWorkflow.getWorkflowItems().get(0).getCreateTs());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getCreateUser(),
        receivingWorkflow.getWorkflowItems().get(0).getCreateUser());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getLastChangedTs(),
        receivingWorkflow.getWorkflowItems().get(0).getLastChangedTs());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getLastChangedUser(),
        receivingWorkflow.getWorkflowItems().get(0).getLastChangedUser());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getGtin(),
        receivingWorkflow.getWorkflowItems().get(0).getGtin());
    Assert.assertEquals(
        response.getWorkflowItems().get(0).getItemTrackingId(),
        receivingWorkflow.getWorkflowItems().get(0).getItemTrackingId());
  }

  @Test
  public void testTransformContainerToWorkflowRequest() {
    RcWorkflowCreateRequest response =
        receivingWorkflowTransformer.transformContainerToWorkflowRequest(
            receiveContainerRequest, WorkflowType.FRAUD, "10001L");

    Assert.assertEquals(response.getType(), WorkflowType.FRAUD);
    Assert.assertEquals(
        response.getCreateReason(), receiveContainerRequest.getWorkflowCreateReason());
    Assert.assertEquals(response.getWorkflowId(), receiveContainerRequest.getWorkflowId());
    Assert.assertEquals(
        response.getPackageBarcodeValue(), receiveContainerRequest.getScannedLabel());
    Assert.assertEquals(
        response.getPackageBarcodeType(), receiveContainerRequest.getScannedLabelType());
    Assert.assertEquals(response.getItems().size(), 1);
    Assert.assertEquals(
        response.getItems().get(0).getGtin(), receiveContainerRequest.getScannedItemLabel());
    Assert.assertEquals(response.getItems().get(0).getItemTrackingId(), new String("10001L"));
  }
}
