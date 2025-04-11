package com.walmart.move.nim.receiving.sib.utils;

import static com.walmart.move.nim.receiving.sib.utils.Constants.MARKET_FULFILLMENT_CENTER;
import static com.walmart.move.nim.receiving.sib.utils.Constants.MFC;
import static com.walmart.move.nim.receiving.sib.utils.Constants.STORE;
import static io.strati.libs.commons.lang.StringUtils.equalsIgnoreCase;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import com.walmart.move.nim.receiving.sib.model.ei.LineItem;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

public class Util {

  public static Date addHoursToJavaUtilDate(Date date, int hours) {
    Instant instant = date.toInstant();
    Instant newInstant = instant.plusSeconds(3600L * hours);
    return Date.from(newInstant);
  }

  public static Date addMinsToJavaUtilDate(Date date, int min) {
    Instant instant = date.toInstant();
    Instant newInstant = instant.plusSeconds(60L * min);
    return Date.from(newInstant);
  }

  public static boolean isMorningTime(
      Date unloadTs, String timeZoneCode, SIBManagedConfig sibManagedConfig) {
    ZonedDateTime zonedDateTime =
        unloadTs.toInstant().atZone(TimeZone.getTimeZone(timeZoneCode).toZoneId());
    return zonedDateTime.getHour() >= sibManagedConfig.getReferenceShiftStartHours()
        && zonedDateTime.getHour() < sibManagedConfig.getReferenceShiftEndHours();
  }

  public static void removePallets(List<ASNDocument> asnDocumentList) {
    for (ASNDocument asnDocument : asnDocumentList) {
      List<Pack> packs = new ArrayList<>();
      for (Pack pack : asnDocument.getPacks()) {
        if (!Objects.isNull(pack.getPalletNumber())) {
          continue;
        }
        packs.add(pack);
      }
      asnDocument.setPacks(packs);
    }
  }

  public static String getPackId(Pack pack) {
    return Objects.isNull(pack.getPalletNumber()) ? pack.getPackNumber() : pack.getPalletNumber();
  }

  public static String getPackType(Pack pack) {
    if (!CollectionUtils.isEmpty(pack.getItems())) {
      for (Item item : pack.getItems()) {
        if (!equalsIgnoreCase(MARKET_FULFILLMENT_CENTER, item.getReplenishmentCode())) {
          return STORE;
        }
      }
    }
    return MFC;
  }

  public static Container replaceSSCCWithTrackingId(Container container) {
    container.setTrackingId(container.getSsccNumber());
    return container;
  }

  public static boolean isMFCType(EIEvent eiEvent) {

    Optional<LineItem> optionalEIEvent =
        eiEvent
            .getBody()
            .getLineInfo()
            .stream()
            .filter(line -> Constants.MFC.equalsIgnoreCase(line.getLineMetaInfo().getPalletType()))
            .findAny();
    return optionalEIEvent.isPresent() ? Boolean.FALSE : Boolean.TRUE;
  }

  public static Map<Long, ItemDetails> extractItemMapFromASN(ASNDocument asnDocument) {
    return CoreUtil.getItemMap(asnDocument);
  }

  public static String retrieveWarehouseAreaCode(ItemDetails itemDetails) {

    // wareHouseAreaCode was getting detected as double where as it should be an integer . and
    // hence, converting it to integer
    return Objects.nonNull(itemDetails)
            && Objects.nonNull(itemDetails.getItemAdditonalInformation())
            && Objects.nonNull(itemDetails.getItemAdditonalInformation().get("warehouseAreaCode"))
        ? String.valueOf(
            Double.valueOf(
                    itemDetails.getItemAdditonalInformation().get("warehouseAreaCode").toString())
                .intValue())
        : null;
  }

  public static String getItemType(Item item) {
    return "MARKET_FULFILLMENT_CENTER".equalsIgnoreCase(item.getReplenishmentCode()) ? MFC : STORE;
  }

  public static String getPalletType(List<Pack> packs) {
    if (!CollectionUtils.isEmpty(packs)) {
      for (Pack pack : packs) {
        String packType = getPackType(pack);
        if (STORE.equalsIgnoreCase(packType)) {
          return packType;
        }
      }
      return MFC;
    }
    return STORE;
  }

  public static Map<String, String> getPalletTypeMap(List<Pack> packs) {
    Map<String, String> palletTypeMap = new HashMap<>();
    Map<String, List<Pack>> packMap =
        packs
            .stream()
            .filter(pack -> Objects.nonNull(pack.getPalletNumber()))
            .collect(Collectors.groupingBy(Pack::getPalletNumber));
    packMap.forEach((pallet, _packs) -> palletTypeMap.put(pallet, Util.getPalletType(_packs)));
    return palletTypeMap;
  }
}
