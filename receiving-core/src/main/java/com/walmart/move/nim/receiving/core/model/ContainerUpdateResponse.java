package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.entity.Container;
import java.io.Serializable;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * ContainerUpdateResponse
 *
 * @author vn50o7n
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ContainerUpdateResponse implements Serializable {
  private Container container;
  private Map<String, Object> printJob;
}
