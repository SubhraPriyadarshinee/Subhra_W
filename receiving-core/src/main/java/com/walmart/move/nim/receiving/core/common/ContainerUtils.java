package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.beans.PropertyDescriptor;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Utility methods operating on Container
 *
 * @author jethoma
 */
public class ContainerUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerUtils.class);

  private static final List<String> containerPropertiesToIgnore =
      Arrays.asList(
          "id",
          "trackingId",
          "facilityNum",
          "facilityCountryCode",
          "containerItems",
          "createTs",
          "createUser",
          "publishTs");

  private static final List<String> containerItemPropertiesToIgnore =
      Arrays.asList("id", "trackingId", "facilityNum", "facilityCountryCode");

  private ContainerUtils() {}

  /**
   * Calculate container weight across all items and child containers
   *
   * @param container
   * @return
   */
  public static Float calculateWeight(Container container) {

    Float weight = new Float(0.0);

    // Calculate the weight of the items in this container's collection
    List<ContainerItem> items = container.getContainerItems();
    if (items != null) {
      for (ContainerItem item : items) {
        Integer qtyVendorPack =
            ReceivingUtils.conversionToVendorPack(
                item.getQuantity(), item.getQuantityUOM(), item.getVnpkQty(), item.getWhpkQty());
        weight += item.getVnpkWgtQty() * qtyVendorPack;
      }
    }

    // Recursively calculate weight for items found in child containers
    Set<Container> childContainers = container.getChildContainers();
    if (childContainers != null) {
      for (Container childContainer : childContainers) {
        weight += calculateWeight(childContainer);
      }
    }

    return weight;
  }

  /**
   * Derive the unit of measure for the container weight by looking at the items in the container.
   * If no UOM is found then it will return an empty string since there unit of measure should never
   * be inferred.
   *
   * @param container
   * @return
   */
  public static String getDefaultWeightUOM(Container container) {
    String uom = "";

    // Looking for the first item with a non-empty unit of measure
    List<ContainerItem> items = container.getContainerItems();
    if (items != null) {

      for (ContainerItem item : items) {

        if (!StringUtils.isEmpty(item.getVnpkWgtUom())) {
          uom = item.getVnpkWgtUom();
          break;
        }
      }
    }

    if (!uom.isEmpty()) {
      return uom;
    }

    // If a uom has not been found then recursively examine any child containers
    Set<Container> childContainers = container.getChildContainers();
    if (childContainers != null) {

      for (Container childContainer : childContainers) {
        uom = getDefaultWeightUOM(childContainer);
        if (!uom.isEmpty()) {
          break;
        }
      }
    }

    return uom;
  }

  /**
   * @param containerItem
   * @return
   */
  public static Integer calculateActualHi(ContainerItem containerItem) {
    Integer qtyInVendorPack =
        ReceivingUtils.conversionToVendorPack(
            containerItem.getQuantity(), containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(), containerItem.getWhpkQty());

    return (int) Math.ceil((double) qtyInVendorPack / (double) containerItem.getActualTi());
  }

  public static void setAttributesForImports(
      String poDcNumber, String poDcCountryCode, Boolean importInd, ContainerItem containerItem) {
    if (Boolean.TRUE.equals(importInd)) {
      containerItem.setPoDCNumber(poDcNumber);
      containerItem.setPoDcCountry(poDcCountryCode);
      containerItem.setImportInd(importInd);
    }
  }

  /**
   * Adjust the container based on the new quantity
   *
   * @param cloneContainer
   * @param container
   * @param newQty
   * @return adjusted container
   */
  public static Container adjustContainerByQty(
      boolean cloneContainer, Container container, Integer newQty) {
    Container adjustedContainer = cloneContainer ? SerializationUtils.clone(container) : container;
    ContainerItem adjustedContainerItem = adjustedContainer.getContainerItems().get(0);
    adjustedContainerItem.setQuantity(newQty);
    adjustedContainerItem.setActualHi(ContainerUtils.calculateActualHi(adjustedContainerItem));
    adjustedContainer.setWeight(ContainerUtils.calculateWeight(adjustedContainer));
    adjustedContainer.setWeightUOM(ContainerUtils.getDefaultWeightUOM(adjustedContainer));

    return adjustedContainer;
  }

  /**
   * Adjust the container based on the new quantity
   *
   * @param container
   * @param newQty
   * @return adjusted container
   */
  public static Container adjustContainerByQtyWithoutTiHi(Container container, Integer newQty) {
    ContainerItem adjustedContainerItem = container.getContainerItems().get(0);
    adjustedContainerItem.setQuantity(newQty);
    container.setWeight(ContainerUtils.calculateWeight(container));
    container.setWeightUOM(ContainerUtils.getDefaultWeightUOM(container));

    return container;
  }

  public static Map<String, String> getPrintRequestHeaders(HttpHeaders httpHeaders) {
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
    return headers;
  }

  /**
   * This is a utility method to parse string rotate date to java.util.Date
   *
   * @param strRotateDate
   * @return
   */
  public static Date parseRotateDate(String strRotateDate) {
    if (StringUtils.isEmpty(strRotateDate)) {
      return null;
    }
    DateFormat dateFormat = new SimpleDateFormat(ReceivingConstants.UTC_DATE_FORMAT);
    dateFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
    Date rotateDate;
    try {
      rotateDate = dateFormat.parse(strRotateDate);
    } catch (ParseException e) {
      LOGGER.error(
          ReceivingConstants.EXCEPTION_HANDLER_ERROR_MESSAGE, ExceptionUtils.getStackTrace(e));
      return null;
    }
    return rotateDate;
  }

  public static void populateContainerItemInContainer(
      List<Container> containers, List<ContainerItem> containerItems) {
    Map<String, List<ContainerItem>> containerItemMap = new HashMap<>();

    for (ContainerItem containerItem : containerItems) {
      if (Objects.isNull(containerItemMap.get(containerItem.getTrackingId()))) {
        List<ContainerItem> items = new ArrayList<>();
        items.add(containerItem);
        containerItemMap.put(containerItem.getTrackingId(), items);
        continue;
      }

      List<ContainerItem> items = containerItemMap.get(containerItem.getTrackingId());
      items.add(containerItem);
      containerItemMap.put(containerItem.getTrackingId(), items);
    }

    for (Container container : containers) {
      container.setContainerItems(containerItemMap.get(container.getTrackingId()));
    }
  }

  public static ContainerDTO replaceContainerWithSSCC(ContainerDTO container) {
    if (Objects.nonNull(container.getContainerItems())
        && !container.getContainerItems().isEmpty()) {
      container
          .getContainerItems()
          .stream()
          .filter(
              containerItem ->
                  StringUtils.equalsIgnoreCase(
                      containerItem.getTrackingId(), container.getTrackingId()))
          .forEach(containerItem -> containerItem.setTrackingId(container.getSsccNumber()));
    }
    container.setTrackingId(container.getSsccNumber());
    return container;
  }

  public static void replaceTrackingIdWithSSCC(List<ContainerDTO> containers) {
    containers.stream().forEach(container -> container.setTrackingId(container.getSsccNumber()));
  }

  public static boolean isAtlasConvertedItem(ContainerItem containerItem) {
    return Objects.nonNull(containerItem.getContainerItemMiscInfo())
            && Objects.nonNull(
                containerItem
                    .getContainerItemMiscInfo()
                    .get(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM))
        ? Boolean.valueOf(
            containerItem
                .getContainerItemMiscInfo()
                .get(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM))
        : false;
  }

  public static void copyContainerProperties(Container dest, Container orig) {
    PropertyUtilsBean propertyUtilsBean = BeanUtilsBean.getInstance().getPropertyUtils();
    PropertyDescriptor[] origDescriptors = propertyUtilsBean.getPropertyDescriptors(orig);

    copyProperties(propertyUtilsBean, origDescriptors, containerPropertiesToIgnore, dest, orig);
  }

  public static void copyContainerItemProperties(ContainerItem dest, ContainerItem orig) {
    PropertyUtilsBean propertyUtilsBean = BeanUtilsBean.getInstance().getPropertyUtils();
    PropertyDescriptor[] origDescriptors = propertyUtilsBean.getPropertyDescriptors(orig);

    copyProperties(propertyUtilsBean, origDescriptors, containerItemPropertiesToIgnore, dest, orig);
  }

  public static void copyProperties(
      PropertyUtilsBean propertyUtilsBean,
      PropertyDescriptor[] origDescriptors,
      List<String> propertiesToIgnore,
      Object dest,
      Object orig) {

    for (PropertyDescriptor descriptor : origDescriptors) {
      String name = descriptor.getName();
      if (propertiesToIgnore.contains(name)) {
        continue;
      }
      try {
        Object value = propertyUtilsBean.getSimpleProperty(orig, name);
        propertyUtilsBean.setSimpleProperty(dest, name, value);
      } catch (Exception e) {
        // handle the exception as needed
      }
    }
  }
}
