package com.walmart.move.nim.receiving.endgame.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.Datum;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequest;
import com.walmart.move.nim.receiving.endgame.model.LabelResponse;
import com.walmart.move.nim.receiving.endgame.model.PrintRequest;
import com.walmart.move.nim.receiving.endgame.model.SlotLocation;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public final class LabelUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(LabelUtils.class);
  private static final JsonParser JSON_PARSER = new JsonParser();

  private LabelUtils() {}

  public static LabelResponse generateLabelResponse(
      LabelRequest labelRequest, EndGameLabelData labelData, HttpHeaders httpHeaders) {
    List<PrintRequest> printRequests = new ArrayList<>();

    String formatName = "";
    if (labelRequest.getLabelType().equals(LabelType.TCL.getType())) {
      formatName = EndgameConstants.TCL_LABEL_FORMAT_NAME;
    } else if (labelRequest.getLabelType().equals(LabelType.TPL.getType())) {
      formatName = EndgameConstants.TPL_LABEL_FORMAT_NAME;
    }

    String finalFormatName = formatName;
    labelData
        .getTrailerCaseLabels()
        .forEach(
            tcl -> {
              List<Datum> datumList = getData(labelRequest, httpHeaders, tcl, finalFormatName);
              printRequests.add(
                  PrintRequest.builder()
                      .labelIdentifier(tcl)
                      .formatName(finalFormatName)
                      .ttlInHours(EndgameConstants.LABEL_TTL)
                      .data(datumList)
                      .build());
            });
    LabelResponse response = new LabelResponse();
    response.setClientId(EndgameConstants.UI_CLIENT_ID);
    response.setPrintRequests(printRequests);
    Map<String, String> headers = new HashMap<>();
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    headers.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    response.setHeaders(headers);
    return response;
  }

  /**
   * @param labelRequest
   * @param headers
   * @param tcl
   * @param formatname
   * @return
   */
  public static List<Datum> getData(
      LabelRequest labelRequest, HttpHeaders headers, String tcl, String formatname) {
    List<Datum> datumList = new ArrayList<>();
    datumList.add(Datum.builder().key("trailer").value(labelRequest.getTrailerId()).build());
    datumList.add(Datum.builder().key("Date").value(ReceivingUtils.dateInEST()).build());
    datumList.add(
        Datum.builder()
            .key("deliveryNumber")
            .value(labelRequest.getDeliveryNumber().toString())
            .build());

    datumList.add(Datum.builder().key("DESTINATION").value("").build());

    datumList.add(Datum.builder().key("Qty").value("").build());

    datumList.add(Datum.builder().key("ITEM").value("").build());

    datumList.add(Datum.builder().key("DESC").value("").build());

    datumList.add(Datum.builder().key("UPCBAR").value("").build());

    datumList.add(
        Datum.builder()
            .key("user")
            .value(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY))
            .build());
    datumList.add(Datum.builder().key("TCL").value(tcl).build());
    String tclPrefix = tcl.substring(0, tcl.length() - 4);
    String tclsuffix = tcl.substring(tcl.length() - 4);
    datumList.add(Datum.builder().key("TCLPREFIX").value(tclPrefix).build());
    datumList.add(Datum.builder().key("TCLSUFFIX").value(tclsuffix).build());
    return datumList;
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Retrieve location for the given container
   *
   * @param locations the list of location from slot response
   * @param trackingId the container tracking id
   * @return the slot location
   */
  public static SlotLocation retrieveLocation(List<SlotLocation> locations, String trackingId) {

    Optional<SlotLocation> locationOptional =
        locations
            .stream()
            .filter(location -> trackingId.equals(location.getContainerTrackingId()))
            .findFirst();

    if (locationOptional.isPresent()) {
      return locationOptional.get();
    }

    LOGGER.error(EndgameConstants.LOG_SLOTTING_NO_LOCATION_RESPONSE, trackingId);

    throw new ReceivingBadDataException(
        ExceptionCodes.INVALID_SLOTTING_REQ,
        String.format(
            EndgameConstants.SLOTTING_BAD_RESPONSE_ERROR_MSG,
            HttpStatus.UNPROCESSABLE_ENTITY,
            trackingId));
  }

  public static Map<String, SlotLocation> retrieveLocation(List<SlotLocation> locations) {

    return locations
        .stream()
        .collect(
            Collectors.toMap(
                slotLocation -> slotLocation.getContainerTrackingId(),
                slotLocation -> slotLocation));
  }

  public static String getTCLTemplate(String printableZPLTemplate) {
    JsonObject jsonObjectZPLTemplate = JSON_PARSER.parse(printableZPLTemplate).getAsJsonObject();
    Object template = jsonObjectZPLTemplate.get(String.valueOf(TenantContext.getFacilityNum()));
    return Objects.isNull(template)
        ? jsonObjectZPLTemplate.get("default").getAsString()
        : String.valueOf(template);
  }
}
