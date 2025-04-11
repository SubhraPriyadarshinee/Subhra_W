package com.walmart.move.nim.receiving.core.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.util.Pair;

/** Uom utils for scaling quantities to desired UOM */
public class UomUtils {

  /** static map containing multipliers for standard metric system prefixes */
  private static final Map<String, Double> uomMultiplierMap =
      new HashMap<String, Double>() {
        {
          put("nano", 1000000000.0);
          put("micro", 1000000.0);
          put("milli", 1000.0);
          put("centi", 100.0);
          put("deci", 10.0);
        }
      };

  /**
   * Gets scaled quantity. Passing quantity not in its base UOM, passing a non-scalable UOM or
   * passing incorrect prefix will give incorrect results. Ex: 12.34 LB -> 1234,centi-LB
   *
   * @param baseQuantity the quantity in base UOM
   * @param baseQuantityUom the quantity uom
   * @param targetUomPrefix the target UOM prefix: "deci", "centi", "milli" (Preferably from CCM)
   * @param scalableUomList the scalable uom list: ["LB" , "OZ"] (Preferably from CCM)
   * @return the scaled quantity & UOM
   */
  public static Pair<Integer, String> getScaledQuantity(
      Double baseQuantity,
      String baseQuantityUom,
      String targetUomPrefix,
      List<String> scalableUomList) {
    if (Objects.isNull(baseQuantity)) {
      return null;
    }
    String scaledUom = getScaledUom(baseQuantityUom, targetUomPrefix, scalableUomList);
    if (scalableUomList.contains(baseQuantityUom)) {
      Double multiplier = uomMultiplierMap.get(targetUomPrefix);
      if (Objects.nonNull(multiplier)) {
        return Pair.of(new Float(baseQuantity * multiplier).intValue(), scaledUom);
      }
    }
    return Pair.of(baseQuantity.intValue(), baseQuantityUom);
  }

  /**
   * Gets scaled quantity with UOM and if it's null, returns the default value passed
   *
   * @param baseQuantity the quantity
   * @param baseQuantityUom the quantity uom
   * @param targetUomPrefix the target uom prefix: "deci", "centi", "milli" (Preferably from CCM)
   * @param scalableUomList the scalable uom list: ["LB" , "OZ"] (Preferably from CCM)
   * @param defaultValue the default value
   * @return the scaled quantity & UOM
   */
  public static Pair<Integer, String> getScaledQuantity(
      Double baseQuantity,
      String baseQuantityUom,
      String targetUomPrefix,
      List<String> scalableUomList,
      int defaultValue) {
    Pair<Integer, String> scaledQuantity =
        getScaledQuantity(baseQuantity, baseQuantityUom, targetUomPrefix, scalableUomList);
    String scaledUom = getScaledUom(baseQuantityUom, targetUomPrefix, scalableUomList);
    return Objects.nonNull(scaledQuantity) ? scaledQuantity : Pair.of(defaultValue, scaledUom);
  }

  /**
   * Gets base unit quantity. Ex: 1234 centi-LB -> 12.34,LB
   *
   * @param scaledQuantity the quantity
   * @param scaledQuantityUom the quantity UOM: "centi-OZ", "milli-LB"
   * @return the base unit quantity & UOM
   */
  public static Pair<Double, String> getBaseUnitQuantity(
      Long scaledQuantity, String scaledQuantityUom) {
    if (Objects.isNull(scaledQuantity)) {
      return null;
    }
    String prefix = Arrays.stream(scaledQuantityUom.split("-")).findFirst().get().toLowerCase();
    Double multiplier = uomMultiplierMap.get(prefix);
    String baseUom = getBaseUom(scaledQuantityUom);
    if (Objects.nonNull(multiplier)) {
      return Pair.of(scaledQuantity / multiplier, baseUom);
    }
    return Pair.of(Double.valueOf(scaledQuantity), baseUom);
  }

  /**
   * Gets base unit quantity and if it's null, returns the default value passed
   *
   * @param scaledQuantity the quantity
   * @param scaledQuantityUom the quantity uom
   * @param defaultValue the default value
   * @return the base unit quantity & UOM
   */
  public static Pair<Double, String> getBaseUnitQuantity(
      Long scaledQuantity, String scaledQuantityUom, double defaultValue) {
    Pair<Double, String> baseUnitQuantity = getBaseUnitQuantity(scaledQuantity, scaledQuantityUom);
    String baseUom = getBaseUom(scaledQuantityUom);
    return Objects.nonNull(baseUnitQuantity) ? baseUnitQuantity : Pair.of(defaultValue, baseUom);
  }

  /**
   * Gets base unit quantity. Ex: 1234 centi-LB -> 12.34,LB
   *
   * @param scaledQuantity the quantity
   * @param scaledQuantityUom the quantity UOM: "centi-OZ", "milli-LB"
   * @return the base unit quantity
   */
  public static Pair<Double, String> getBaseUnitQuantity(
      Integer scaledQuantity, String scaledQuantityUom) {
    return Objects.nonNull(scaledQuantity)
        ? getBaseUnitQuantity(new Long(scaledQuantity), scaledQuantityUom)
        : null;
  }

  /**
   * Gets base unit quantity and if it's null, returns the default value passed
   *
   * @param scaledQuantity the quantity
   * @param scaledQuantityUom the quantity uom
   * @param defaultValue the default value
   * @return the base unit quantity & UOM
   */
  public static Pair<Double, String> getBaseUnitQuantity(
      Integer scaledQuantity, String scaledQuantityUom, double defaultValue) {
    Pair<Double, String> baseUnitQuantity = getBaseUnitQuantity(scaledQuantity, scaledQuantityUom);
    String baseUom = getBaseUom(scaledQuantityUom);
    return Objects.nonNull(baseUnitQuantity) ? baseUnitQuantity : Pair.of(defaultValue, baseUom);
  }

  /**
   * Gets derived uom. Ex: (LB, centi) -> centi-LB
   *
   * @param baseQuantityUom the quantity uom
   * @param targetUomPrefix the target uom prefix: "deci", "centi", "milli" (Preferably from CCM)
   * @param scalableUomList the scalable uom list: ["LB" , "OZ"] (Preferably from CCM)
   * @return the derived uom
   */
  public static String getScaledUom(
      String baseQuantityUom, String targetUomPrefix, List<String> scalableUomList) {
    return scalableUomList.contains(baseQuantityUom)
        ? targetUomPrefix.toLowerCase() + "-" + baseQuantityUom
        : baseQuantityUom;
  }

  /**
   * Gets base uom from a modified one. Ex: centi-LB -> LB
   *
   * @param scaledQuantityUom the quantity uom
   * @return the base uom
   */
  public static String getBaseUom(String scaledQuantityUom) {
    return Objects.isNull(scaledQuantityUom)
        ? null
        : Arrays.stream(scaledQuantityUom.split("-")).reduce((a, b) -> b).get();
  }
}
