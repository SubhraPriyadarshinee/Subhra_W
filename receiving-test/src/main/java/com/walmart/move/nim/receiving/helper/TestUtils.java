package com.walmart.move.nim.receiving.helper;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * static utility methods for testing, please add common test related utils here
 *
 * @author i0a02l3
 */
public class TestUtils {

  /**
   * Parse a String URL to URL object, and get map of query parameters, Also handles duplicate query
   * params with same value
   *
   * @param url
   * @return
   */
  public static Map<String, String> getQueryMap(String url) throws MalformedURLException {
    return getQueryMap(new URL(url));
  }

  public static Map<String, String> getQueryMap(URL url) {
    String queryString =
        Optional.ofNullable(url.getQuery()).orElse(ReceivingConstants.EMPTY_STRING);
    return Arrays.stream(queryString.split("&"))
        .collect(
            Collectors.toMap(
                param -> param.split("=")[0],
                param -> param.split("=")[1],
                (v1, v2) -> {
                  if (Objects.equals(v1, v2)) {
                    return v1;
                  }
                  throw new IllegalStateException(
                      MessageFormat.format(
                          "value mismatch on duplicate key between {0} and {1}", v1, v2));
                }));
  }
}
