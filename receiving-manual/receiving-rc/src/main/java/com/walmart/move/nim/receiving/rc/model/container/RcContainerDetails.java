package com.walmart.move.nim.receiving.rc.model.container;

import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class RcContainerDetails {
  @Valid
  @NotNull(message = "containerRLog cannot be null")
  private ContainerRLog containerRLog;

  private List<ItemTracker> itemTrackers;
  private ReceivingWorkflow receivingWorkflow;
}
