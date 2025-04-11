package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.client.gls.model.*;
import com.walmart.move.nim.receiving.core.common.ContainerValidationUtils;
import com.walmart.move.nim.receiving.core.common.GdcPutawayPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.ContainerItemRequest;
import com.walmart.move.nim.receiving.core.model.ContainerRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * This service enables interface testing Putaway Messages for Witron interface testing
 *
 * @author jethoma
 */
@Service
public class WitronITService {
  private static final Logger log = LoggerFactory.getLogger(WitronITService.class);
  @Autowired private ReceiveContainerService receiveContainerService;
  @Autowired private ContainerService containerService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ContainerRepository containerRepository;
  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;

  /**
   * route request to the appropriate interface test case
   *
   * @throws ReceivingException
   * @throws IOException
   */
  public String routeTestCase(
      String testCase,
      String trackingId,
      Long deliveryNumber,
      Integer itemNumber,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    ContainerRequest request = null;
    String assignedTrackingId = null;
    Container container = null;
    List<ContainerItem> containerItems = null;
    Container parentContainer = null;

    String jsonFile = "witron-it/" + testCase.toUpperCase() + ".json";

    switch (testCase.toUpperCase()) {
      case "RCV-00":
        request = getContainerRequest("witron-it/RCV-01.json");
        assignedTrackingId = (trackingId == null) ? getRandomTrackingId18() : trackingId;
        request.setTrackingId(assignedTrackingId);
        request.setMessageId(assignedTrackingId);
        setContainerItem(request, itemNumber);
        ContainerValidationUtils.validateContainerRequest(request);
        receiveContainerService.receiveContainer(deliveryNumber, request, httpHeaders);
        log.info("{} sent, trackingId {}", testCase, assignedTrackingId);
        break;

      case "RCV-08":
        // void container
        containerService.backoutContainer(trackingId, httpHeaders);
        log.info("Performing Backout on {}", trackingId);
        request = new ContainerRequest();
        request.setTrackingId(trackingId);
        break;

      case "RCV-05":
        // test RCV-05 requires an invalid tracking id
        request = getContainerRequest(jsonFile);
        assignedTrackingId = getRandomTrackingId20();
        request.setMessageId(assignedTrackingId);
        request.setTrackingId(assignedTrackingId);
        ContainerValidationUtils.validateContainerRequest(request);
        receiveContainerService.receiveContainer(deliveryNumber, request, httpHeaders);
        log.info("{} sent, trackingId {}", testCase, assignedTrackingId);
        break;

      case "RCV-13-HOLD":
        // put pallet on hold
        parentContainer = containerRepository.findByTrackingId(trackingId);
        container = containerService.getContainerIncludingChild(parentContainer);
        containerItems = containerItemRepository.findByTrackingId(trackingId);
        container.setContainerItems(containerItems);
        container.setInventoryStatus("WORK_IN_PROGRESS");
        gdcPutawayPublisher.publishMessage(
            container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
        return container.getTrackingId();

      case "RCV-13-AVAILABLE":
        // put pallet on hold
        parentContainer = containerRepository.findByTrackingId(trackingId);
        container = containerService.getContainerIncludingChild(parentContainer);
        containerItems = containerItemRepository.findByTrackingId(trackingId);
        container.setContainerItems(containerItems);
        container.setInventoryStatus("AVAILABLE");
        gdcPutawayPublisher.publishMessage(
            container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
        return container.getTrackingId();

      case "RTU-DELETE":
        container =
            containerPersisterService.getContainerWithChildContainersExcludingChildContents(
                trackingId);
        if (container == null) {
          return "can't delete as no container found for lpn =" + trackingId;
        }

        container.setMessageId("RCV-RTU-DELETE-" + trackingId);
        gdcPutawayPublisher.publishMessage(
            container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);
        return "Delete message sent to RTU/Putaway for lpn = " + trackingId;

      default:
        // everything else uses preset data scenario
        request = getContainerRequest(jsonFile);
        assignedTrackingId = getRandomTrackingId18();
        request.setMessageId(assignedTrackingId);
        request.setTrackingId(assignedTrackingId);
        ContainerValidationUtils.validateContainerRequest(request);
        receiveContainerService.receiveContainer(deliveryNumber, request, httpHeaders);
        log.info("{} sent, trackingId {}", testCase, assignedTrackingId);
    }

    return request.getTrackingId();
  }

  /**
   * Load a container request from the given file name
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  private ContainerRequest getContainerRequest(String fileName) {
    String fileText = getResourceFile(fileName);
    ContainerRequest request = new Gson().fromJson(fileText, ContainerRequest.class);
    return request != null ? request : new ContainerRequest();
  }

  /**
   * @param fileName
   * @return
   */
  private String getResourceFile(String fileName) {
    String fileText = "";

    URL fileUrl = getClass().getClassLoader().getResource(fileName);
    if (fileUrl == null) {
      log.error("{} : file not found", fileName);
      return fileText;
    }

    Path path;
    try {
      path = Paths.get(fileUrl.toURI());
    } catch (URISyntaxException ue) {
      log.error("{} : {}", fileName, ue.getMessage());
      return fileText;
    }

    try {
      byte[] fileBytes = Files.readAllBytes(path);
      fileText = new String(fileBytes);
    } catch (IOException ioe) {
      log.error("{} : {}", fileName, ioe.getMessage());
      fileText = "";
    }
    return fileText;
  }

  /**
   * generate a random 18-digit tracking id
   *
   * @return
   */
  private String getRandomTrackingId18() {
    Calendar cal = Calendar.getInstance();
    int serialId1 = (int) (getSecureRandom() * 9999998 + 1);
    int serialId2 = (int) (getSecureRandom() * 9999998 + 1);
    return String.format("0%03d%07d%07d", cal.get(Calendar.DAY_OF_YEAR), serialId1, serialId2);
  }

  /**
   * generate a random 20-digit tracking id
   *
   * @return
   */
  private String getRandomTrackingId20() {
    Calendar cal = Calendar.getInstance();
    int serialId1 = (int) (getSecureRandom() * 99999998 + 1);
    int serialId2 = (int) (getSecureRandom() * 99999998 + 1);
    return String.format("0%03d%08d%08d", cal.get(Calendar.DAY_OF_YEAR), serialId1, serialId2);
  }
  /**
   * @param itemNumber
   * @return
   */
  private JsonObject getItemJson(Integer itemNumber) {
    String itemFile = "witron-it/item-" + itemNumber + ".json";
    String itemJsonString = getResourceFile(itemFile);
    JsonParser jsonParser = new JsonParser();
    return jsonParser.parse(itemJsonString).getAsJsonObject();
  }

  private void setContainerItem(ContainerRequest request, Integer itemNumber) {
    JsonObject item = getItemJson(itemNumber);

    ContainerItemRequest ci = request.getContents().get(0);
    ci.setPurchaseReferenceLineNumber(item.get("poLine").getAsInt());
    ci.setItemNumber(item.get("itemNumber").getAsLong());
    ci.setDescription(item.get("description").getAsString());
    ci.setDeptNumber(item.get("deptNumber").getAsInt());
    ci.setVendorNumber(item.get("vendorNbr").getAsInt());
    ci.setGtin(item.get("gtin").getAsString());
    ci.setWhpkQty(item.get("whpkQty").getAsInt());
    ci.setVendorPackCost(item.get("vendorPackCost").getAsDouble());
    ci.setWhpkSell(item.get("whpkSell").getAsDouble());

    // Create a full pallet
    int vnpkQty = item.get("vnpkQty").getAsInt();
    ci.setVnpkQty(vnpkQty);
    int ti = item.get("ti").getAsInt();
    int hi = item.get("hi").getAsInt();
    ci.setQuantity(ti * hi * vnpkQty);
    ci.setActualTi(ti);
    ci.setActualHi(hi);

    // calculate weight
    Float vnpkWeight = item.get("vendorPackWeight").getAsFloat();
    request.setCtrWght(vnpkQty * vnpkWeight);

    // calculate cube and set
    Float vnpkCube = item.get("vendorPackCubeQty").getAsFloat();
    request.setCtrCube(vnpkQty * vnpkCube);
  }

  public static double getSecureRandom() {
    return new SecureRandom().nextDouble();
  }

  public String glsReceive(GLSReceiveRequest glsReceiveRequest, HttpHeaders headers) {
    Gson gson = new Gson();
    String jsonFile = "witron-it/gls-receive-response.json";
    String fileText = getResourceFile(jsonFile);
    GlsApiInfo glsApiInfo = gson.fromJson(fileText, GlsApiInfo.class);
    GLSReceiveResponse glsReceiveResponse =
        gson.fromJson(glsApiInfo.getPayload(), GLSReceiveResponse.class);
    glsReceiveResponse.setPalletTagId("TAG-" + new SecureRandom().nextInt(1000000));
    glsReceiveResponse.setSlotId("SLOT-" + new SecureRandom().nextInt(1000));
    glsReceiveResponse.setTimestamp(LocalDateTime.now().toString());
    glsApiInfo.setPayload(gson.toJsonTree(glsReceiveResponse));
    return gson.toJson(glsApiInfo);
  }

  public String createGlsLpn(GlsLpnRequest glsLpnRequest, HttpHeaders httpHeaders) {
    Gson gson = new Gson();
    String jsonFile = "witron-it/gls-createTag-response.json";
    String fileText = getResourceFile(jsonFile);
    GlsApiInfo glsApiInfo = gson.fromJson(fileText, GlsApiInfo.class);

    GlsLpnResponse glsLpnResponse = gson.fromJson(glsApiInfo.getPayload(), GlsLpnResponse.class);
    glsLpnResponse.setPalletTagId("TAG-" + new SecureRandom().nextInt(1000000));
    glsLpnResponse.setTimestamp(LocalDateTime.now().toString());
    glsApiInfo.setPayload(gson.toJsonTree(glsLpnResponse));
    return gson.toJson(glsApiInfo);
  }

  public String glsAdjust(GlsAdjustPayload glsAdjustPayload, HttpHeaders headers) {
    String jsonFile = "witron-it/gls-adjustOrCancel-response.json";
    return getResourceFile(jsonFile);
  }

  public String glsGetDeliveryDetails(String deliveryNumber, HttpHeaders headers) {
    Gson gson = new Gson();
    String jsonFile = "witron-it/gls-deliveryDetails-response.json";
    String fileText = getResourceFile(jsonFile);
    GlsApiInfo glsApiInfo = gson.fromJson(fileText, GlsApiInfo.class);
    GLSDeliveryDetailsResponse glsDeliveryDetailsResponse =
        gson.fromJson(glsApiInfo.getPayload(), GLSDeliveryDetailsResponse.class);
    glsDeliveryDetailsResponse.setDeliveryNumber(deliveryNumber);
    AtomicInteger counter = new AtomicInteger();
    glsDeliveryDetailsResponse
        .getPos()
        .forEach(
            po -> {
              String poAppend = "00" + (counter.incrementAndGet());
              po.setPoNumber(deliveryNumber + poAppend);
            });
    glsApiInfo.setPayload(gson.toJsonTree(glsDeliveryDetailsResponse));
    return gson.toJson(glsApiInfo);
  }
}
