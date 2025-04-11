package com.walmart.move.nim.receiving.core.model;

import java.util.Collection;
import lombok.*;

@Data
@Builder
@ToString
@EqualsAndHashCode
public class PaginatedResponse<T> {
  private int pageOffset;
  private int pageSize;
  private int totalPages;
  private long totalCount;
  private Collection<T> results;
}
