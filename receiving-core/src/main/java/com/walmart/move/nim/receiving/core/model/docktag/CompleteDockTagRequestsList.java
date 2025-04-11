package com.walmart.move.nim.receiving.core.model.docktag;

import java.util.List;
import javax.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CompleteDockTagRequestsList {
  @Valid List<CompleteDockTagRequest> list;
}
