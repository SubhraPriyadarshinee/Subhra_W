package com.walmart.move.nim.receiving.acc.util;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintableUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrintableUtils.class);
  @ManagedConfiguration private ACCManagedConfig accManagedConfig;
  private static final String EQUALS_OPERATOR = "=";

  /*
   * @param printableLabelDataRequests
   * @param printerType type of printer for which data should be mapped. Allowed values are MONARCH,
   *     ZEBRA, SATO.
   * @param printerMode - Mode of printer for which data should be mapped printer selection -
   *     allowed values MONARCH - Continuous, Peel, Liner_takeup - ZEBRA - Continuous - SATO -
   *     Continuous.
   */
  public String getLabelData(
      List<PrintableLabelDataRequest> printableLabelDataRequests,
      String printerType,
      String printerMode) {

    LOGGER.info(
        "Getting label for request: {} in the format of {}, mode {}",
        printableLabelDataRequests,
        printerType,
        printerMode);

    String printableZpl = accManagedConfig.getAccPrintableZPL();
    List<Pair<String, String>> labelData = printableLabelDataRequests.get(0).getLabelData();
    return mergeDataAndFormat(printableZpl, labelData).toString();
  }

  private StringBuilder mergeDataAndFormat(String format, List<Pair<String, String>> labelData) {

    StringBuilder result = new StringBuilder(format);

    for (Pair<String, String> label : labelData) {
      if (label.getValue() == null) {
        // setting empty quotes if "null" is passed as value for a key
        LOGGER.warn(
            "\"null\" value is provided for key: {}, so setting it to empty string.",
            label.getKey());
        label.setValue("");
      }
      if (label.getKey() == null) {
        // setting empty quotes if "null" is passed as value for a key
        LOGGER.warn(
            "\"null\" key is provided for value: {}, so setting it to empty string.",
            label.getValue());
        label.setKey("");
      }
      String variable = EQUALS_OPERATOR + label.getKey().trim() + EQUALS_OPERATOR;
      result =
          new StringBuilder(
              result
                  .toString()
                  .replaceAll(variable, Matcher.quoteReplacement(label.getValue().trim())));
    }
    return result;
  }
}
