package com.walmart.move.nim.receiving.rx.model;

import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author v0k00fe */
@Getter
@Setter
@ToString
public class SerializedContainerUpdateRequest extends ContainerUpdateRequest {
  private static final Logger LOG = LoggerFactory.getLogger(SerializedContainerUpdateRequest.class);

  @NotEmpty private List<String> trackingIds;
}
