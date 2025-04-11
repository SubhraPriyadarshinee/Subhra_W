package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.model.FreightType.SC;
import static com.walmart.move.nim.receiving.sib.utils.Constants.BANNER_DESCRIPTION;
import static com.walmart.move.nim.receiving.sib.utils.Constants.MEAT_WHSE_CODE;
import static com.walmart.move.nim.receiving.sib.utils.Constants.NEIGHBORHOOD_MARKET;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_OV;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_RECEIVED;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.model.FreightType;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreightTypeResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(FreightTypeResolver.class);

  public static FreightType resolveFreightType(List<ContainerDTO> containers) {
    if (CollectionUtils.isNotEmpty(containers)) {
      Optional<ContainerDTO> meatProduceContainer =
          containers
              .stream()
              .filter(
                  containerDTO ->
                      containerDTO
                          .getContainerItems()
                          .stream()
                          .anyMatch(
                              containerItem ->
                                  MEAT_WHSE_CODE.equals(containerItem.getWarehouseAreaCode())))
              .findAny();

      boolean isNHM =
          containers
              .stream()
              .anyMatch(
                  containerDTO ->
                      NEIGHBORHOOD_MARKET.equals(
                          Objects.nonNull(containerDTO.getContainerMiscInfo())
                              ? containerDTO.getContainerMiscInfo().get(BANNER_DESCRIPTION)
                              : StringUtils.EMPTY));

      boolean isOverage =
          containers
              .stream()
              .anyMatch(
                  containerDTO ->
                      Arrays.asList(PROBLEM_OV, PROBLEM_RECEIVED)
                          .contains(containerDTO.getContainerStatus()));

      if (meatProduceContainer.isPresent()) {
        LOGGER.info("Meat Produce container:: {}", containers);
        return FreightType.MEAT_PRODUCE;
      } else if (isNHM) {
        LOGGER.info("NHM  container:: {}", containers);
        return FreightType.NHM;
      } else if (isOverage) {
        LOGGER.info("Overage  container:: {}", containers);
        return FreightType.OVERAGE;
      }
    }
    return SC;
  }

  public static FreightType resolveFreightType(ContainerDTO containerDTO) {

    if (Objects.isNull(containerDTO)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_DATA, "Not a valid container to determine freight type");
    }
    Boolean isMeatProduce =
        containerDTO
            .getContainerItems()
            .stream()
            .anyMatch(containerItem -> MEAT_WHSE_CODE.equals(containerItem.getWarehouseAreaCode()));

    Boolean isNHM =
        containerDTO
            .getContainerItems()
            .stream()
            .anyMatch(
                containerItem ->
                    NEIGHBORHOOD_MARKET.equals(
                        Objects.nonNull(containerDTO.getContainerMiscInfo())
                            ? containerDTO.getContainerMiscInfo().get(BANNER_DESCRIPTION)
                            : StringUtils.EMPTY));

    return isMeatProduce ? FreightType.MEAT_PRODUCE : isNHM ? FreightType.NHM : SC;
  }

  public static FreightType resolveFreightType(EIEvent eiEvent) {
    if (Objects.isNull(eiEvent)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_DATA, "Not a valid eiEvent to determine freight type");
    }
    Boolean isMeatProduce =
        eiEvent
            .getBody()
            .getLineInfo()
            .stream()
            .anyMatch(
                lineItem ->
                    MEAT_WHSE_CODE.equals(lineItem.getLineMetaInfo().getWareHouseAreaCode()));

    Boolean isNHM =
        eiEvent
            .getBody()
            .getLineInfo()
            .stream()
            .anyMatch(
                lineItem ->
                    NEIGHBORHOOD_MARKET.equals(lineItem.getLineMetaInfo().getBannerDescription()));

    return isMeatProduce ? FreightType.MEAT_PRODUCE : isNHM ? FreightType.NHM : SC;
  }
}
