package com.walmart.move.nim.receiving.core.client.move;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MOVE_STATUS;
import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MoveRestApiClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(MoveRestApiClient.class);
  public static final String HAUL = "haul";
  public static final String PUTAWAY = "putaway";
  public static final String OPEN = "open";
  public static final String FAILED = "failed";

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired protected Gson gson;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  public MoveRestApiClient() {
    gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
  }

  /**
   * Move - Get Move Container by containerId.
   *
   * <pre>
   * containerId
   * </pre>
   *
   * @return MoveContainers
   */
  @Timed(
      name = "getMoveContainerByContainerId",
      level1 = "uwms-receiving",
      level2 = "moveRestApiClient",
      level3 = "getMoveContainerByContainerId")
  @ExceptionCounted(
      name = "getMoveContainerByContainerId",
      level1 = "uwms-receiving",
      level2 = "getMoveContainerByContainerId",
      level3 = "moveRestApiClient")
  public JsonArray getMoveContainerByContainerId(String containerId, HttpHeaders httpHeaders)
      throws ReceivingException {
    return gson.fromJson(getMovesResponse(containerId, httpHeaders), JsonArray.class);
  }

  private String getMovesResponse(String containerId, HttpHeaders httpHeaders)
      throws ReceivingException {
    HashMap<String, List<String>> moveRequest = new HashMap<>();
    moveRequest.put(CONTAINER_IDS, Arrays.asList(containerId));
    HttpEntity requestEntity =
        new HttpEntity<>(gson.toJson(moveRequest), getMoveApiHttpHeaders(httpHeaders));
    String endPointUrl = appConfig.getMoveQueryBaseUrl() + GET_MOVES_URL;
    ResponseEntity<String> responseEntity = null;
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    try {
      LOGGER.info(
          "MoveApi:cId={} calling service with URL={}, request={}",
          cId,
          endPointUrl,
          requestEntity);
      responseEntity =
          simpleRestConnector.exchange(endPointUrl, HttpMethod.POST, requestEntity, String.class);
      LOGGER.info("MoveApi:cId={} response={}", cId, responseEntity);
      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        return responseEntity.getBody();
      } else if (responseEntity.getStatusCode().is4xxClientError()) {
        LOGGER.error(
            "MoveApi:cId={} Bad request for move status check for containerId={}, request={}, response={}",
            cId,
            containerId,
            requestEntity,
            responseEntity);
        return null;
      } else {
        LOGGER.error(
            "MoveApi:cId={} Unable to verify move status check for containerId={}, request={}, responseEntity={}",
            cId,
            containerId,
            requestEntity,
            responseEntity);
        throw new ReceivingException(
            String.format(
                "Unable to verify move status for containerId=%s, please contact your supervisor or support.",
                containerId),
            SERVICE_UNAVAILABLE,
            ReceivingException.MOVE_SERVICE_DOWN_ERROR_CODE,
            ReceivingException.MOVE_SERVICE_UNABLE_TO_VERIFY_MSG);
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          "MoveApi:cId={} RestClientResponseException during move status check for containerId={}, request={}, response={}, httpStatus={}, err.msg={}",
          cId,
          containerId,
          requestEntity,
          responseEntity,
          e.getRawStatusCode(),
          e.getMessage());

      if (HttpStatus.CONFLICT.value() == e.getRawStatusCode()) return null;

      throw new ReceivingException(
          String.format(
              "Unable to verify move status for containerId=%s, please contact your supervisor or support.",
              containerId),
          SERVICE_UNAVAILABLE,
          ReceivingException.MOVE_SERVICE_DOWN_ERROR_CODE,
          ReceivingException.MOVE_SERVICE_UNABLE_TO_VERIFY_MSG);
    }
  }

  @Timed(
      name = "getMovesByContainerId",
      level1 = "uwms-receiving",
      level2 = "moveRestApiClient",
      level3 = "getMovesByContainerId")
  @ExceptionCounted(
      name = "getMovesByContainerId",
      level1 = "uwms-receiving",
      level2 = "getMovesByContainerId",
      level3 = "moveRestApiClient")
  public List<Move> getMovesByContainerId(String containerId, HttpHeaders httpHeaders)
      throws ReceivingException {
    return gson.fromJson(
        getMovesResponse(containerId, httpHeaders), new TypeToken<List<Move>>() {}.getType());
  }

  private HttpHeaders getMoveApiHttpHeaders(HttpHeaders httpHeaders) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    requestHeaders.add(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
    requestHeaders.add(
        ReceivingConstants.USER_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    requestHeaders.add(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    return requestHeaders;
  }

  public List<String> getMoveContainerDetails(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {

    List<String> moveContainerDetailList = new ArrayList<>();
    JsonArray moveResponse = getMoveContainerByContainerId(trackingId, httpHeaders);
    if (nonNull(moveResponse) && !moveResponse.isEmpty()) {
      JsonArray JsonArrayMembers = moveResponse.getAsJsonArray();
      for (JsonElement attribute : JsonArrayMembers) {
        String moveStatus = EMPTY_STRING;
        String moveType = EMPTY_STRING;
        if (!attribute.getAsJsonObject().get(MOVE_STATUS).isJsonNull())
          moveStatus = attribute.getAsJsonObject().get(MOVE_STATUS).getAsString();
        if (!attribute.getAsJsonObject().get(TYPE).isJsonNull())
          moveType = attribute.getAsJsonObject().get(TYPE).getAsString();
        moveContainerDetailList.add((moveType + moveStatus).toLowerCase());
      }
    }
    return moveContainerDetailList;
  }
}
