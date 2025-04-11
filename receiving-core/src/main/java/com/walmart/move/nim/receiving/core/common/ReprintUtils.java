package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class ReprintUtils {

  private ReprintUtils() {}

  private static Map<Integer, String> storeZones = new HashMap<>();
  private static Map<Integer, String> poTypes = new HashMap<>();
  private static Set<String> poEvents = new HashSet<>();
  private static final Logger logger = LoggerFactory.getLogger(ReprintUtils.class);

  static {
    storeZones.put(0, "B");
    storeZones.put(32, "B");
    storeZones.put(38, "E");
    storeZones.put(73, "E");
    storeZones.put(83, "F");
    storeZones.put(90, "F");
    storeZones.put(91, "F");
    storeZones.put(96, "F");
    storeZones.put(98, "F");
    storeZones.put(49, "E");
    storeZones.put(79, "B");
    storeZones.put(71, "A");
    storeZones.put(74, "A");
    storeZones.put(82, "F");
    storeZones.put(84, "E");
    storeZones.put(85, "D");
    storeZones.put(87, "D");
    storeZones.put(92, "F");
    storeZones.put(95, "F");
    storeZones.put(99, "D");
    storeZones.put(1, "F");
    storeZones.put(2, "E");
    storeZones.put(3, "E");
    storeZones.put(4, "C");
    storeZones.put(5, "D");
    storeZones.put(6, "D");
    storeZones.put(7, "C");
    storeZones.put(8, "D");
    storeZones.put(9, "C");
    storeZones.put(10, "A");
    storeZones.put(11, "A");
    storeZones.put(12, "A");
    storeZones.put(13, "C");
    storeZones.put(14, "A");
    storeZones.put(15, "A");
    storeZones.put(16, "D");
    storeZones.put(17, "A");
    storeZones.put(18, "A");
    storeZones.put(19, "D");
    storeZones.put(20, "E");
    storeZones.put(21, "E");
    storeZones.put(22, "E");
    storeZones.put(23, "B");
    storeZones.put(24, "B");
    storeZones.put(25, "B");
    storeZones.put(26, "B");
    storeZones.put(27, "B");
    storeZones.put(28, "B");
    storeZones.put(29, "B");
    storeZones.put(30, "B");
    storeZones.put(31, "B");
    storeZones.put(33, "B");
    storeZones.put(34, "B");
    storeZones.put(35, "B");
    storeZones.put(36, "B");
    storeZones.put(40, "E");
    storeZones.put(41, "D");
    storeZones.put(42, "A");
    storeZones.put(43, "C");
    storeZones.put(44, "D");
    storeZones.put(45, "C");
    storeZones.put(46, "E");
    storeZones.put(47, "E");
    storeZones.put(48, "C");
    storeZones.put(51, "C");
    storeZones.put(52, "D");
    storeZones.put(53, "E");
    storeZones.put(54, "E");
    storeZones.put(55, "D");
    storeZones.put(56, "D");
    storeZones.put(57, "C");
    storeZones.put(59, "E");
    storeZones.put(67, "E");
    storeZones.put(72, "D");
    storeZones.put(78, "B");
  }

  static {
    poEvents.add("IMPORT ASM");
    poEvents.add("IMPORTRPLN");
    poEvents.add("PICKNPACK");
    poEvents.add("QUICK RESP");
    poEvents.add("POS");
    poEvents.add("POSREPWK");
    poEvents.add("POSREPWK00");
    poEvents.add("POSREPWK01");
    poEvents.add("POSREPWK02");
    poEvents.add("POSREPWK03");
    poEvents.add("POSREPWK04");
    poEvents.add("POSREPWK05");
    poEvents.add("POSREPWK06");
    poEvents.add("POSREPWK07");
    poEvents.add("POSREPWK08");
    poEvents.add("POSREPWK09");
    poEvents.add("POSREPWK10");
    poEvents.add("POSREPWK11");
    poEvents.add("POSREPWK12");
    poEvents.add("POSREPWK13");
    poEvents.add("POSREPWK14");
    poEvents.add("POSREPWK15");
    poEvents.add("POSREPWK16");
    poEvents.add("POSREPWK17");
    poEvents.add("POSREPWK18");
    poEvents.add("POSREPWK19");
    poEvents.add("POSREPWK20");
    poEvents.add("POSREPWK21");
    poEvents.add("POSREPWK22");
    poEvents.add("POSREPWK23");
    poEvents.add("POSREPWK24");
    poEvents.add("POSREPWK25");
    poEvents.add("POSREPWK26");
    poEvents.add("POSREPWK27");
    poEvents.add("POSREPWK28");
    poEvents.add("POSREPWK29");
    poEvents.add("POSREPWK30");
    poEvents.add("POSREPWK31");
    poEvents.add("POSREPWK32");
    poEvents.add("POSREPWK33");
    poEvents.add("POSREPWK34");
    poEvents.add("POSREPWK35");
    poEvents.add("POSREPWK36");
    poEvents.add("POSREPWK37");
    poEvents.add("POSREPWK38");
    poEvents.add("POSREPWK39");
    poEvents.add("POSREPWK40");
    poEvents.add("POSREPWK41");
    poEvents.add("POSREPWK42");
    poEvents.add("POSREPWK43");
    poEvents.add("POSREPWK44");
    poEvents.add("POSREPWK45");
    poEvents.add("POSREPWK46");
    poEvents.add("POSREPWK47");
    poEvents.add("POSREPWK48");
    poEvents.add("POSREPWK49");
    poEvents.add("POSREPWK50");
    poEvents.add("POSREPWK51");
    poEvents.add("POSREPWK52");
    poEvents.add("POS REPLEN");
    poEvents.add("POS REPLN");
    poEvents.add("RPLNSH");
    poEvents.add("WPMREP");
  }

  // PoType to PoCode mapping
  static {
    poTypes.put(23, "AD");
    poTypes.put(33, "AD");
    poTypes.put(73, "AD");
    poTypes.put(83, "AD");
    poTypes.put(93, "AD");

    poTypes.put(20, "WR");
    poTypes.put(22, "WR");
    poTypes.put(40, "WR");
    poTypes.put(42, "WR");
    poTypes.put(50, "WR");

    poTypes.put(10, "WPM");
    poTypes.put(11, "WPM");
    poTypes.put(12, "WPM");
    poTypes.put(14, "WPM");
    poTypes.put(18, "WPM");
    // All others = GO
  }

  public static Template getTemplate(String template) throws IOException {
    StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();
    stringTemplateLoader.putTemplate("TEMPLATE_ID", template);
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_25);
    cfg.setTemplateLoader(stringTemplateLoader);
    return cfg.getTemplate("TEMPLATE_ID");
  }

  public static String truncateDesc(String description) {
    description =
        Optional.ofNullable(description)
            .orElse(ReceivingConstants.EMPTY_STRING)
            .replace("\"", "\\\"");

    if (description != null && description.length() > 20) {
      return description.substring(0, 20);
    } else {
      return description;
    }
  }

  public static String computePoCode(String purchaseReferenceType) {

    String poType = poTypes.get(purchaseReferenceType);

    return poType == null ? "GO" : poType;
  }

  public static String truncateColor(String color) {
    if (color != null && color.length() > 6) return color.substring(0, 6);
    else return color;
  }

  public static String truncateSize(String size) {
    if (size != null && size.length() > 10) return size.substring(0, 6);
    else return size;
  }

  public static String computeEventChar(String poEvent) {
    if (poEvent != null && !poEvent.trim().isEmpty()) {
      if (poEvents.contains(poEvent)) {
        return " ";
      } else {
        return "*";
      }
    } else {
      return " ";
    }
  }

  public static String truncateUser(String userId) {
    if (userId != null && userId.length() > 10) {
      return userId.substring(0, 10);
    }
    return userId;
  }

  public static String computeStoreZone(Integer deptNumber) {
    return storeZones.get(deptNumber);
  }

  public static Writer getPopulatedLabelDataInTemplate(
      Template jsonTemplate, Map<String, Object> placeholders) {
    Writer labelData = new StringWriter();
    try {
      jsonTemplate.process(placeholders, labelData);
    } catch (TemplateException | IOException e) {
      logger.info("Error while replacing placeholders on template.", e);
      return null;
    }
    return labelData;
  }

  public static String getDescription(String description, String exceptionCode) {
    if (!StringUtils.isEmpty(description)) {
      return description;
    } else if (Objects.nonNull(exceptionCode)
        && exceptionCode.equalsIgnoreCase(ContainerException.DOCK_TAG.getText())) {
      return ReceivingConstants.DOCK_TAG;
    } else {
      return ReceivingConstants.PALLET;
    }
  }
}
