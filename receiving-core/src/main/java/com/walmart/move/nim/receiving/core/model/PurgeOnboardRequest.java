package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@NoArgsConstructor
public class PurgeOnboardRequest {
  @NotEmpty List<PurgeEntityType> entities;
}
