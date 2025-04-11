package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import lombok.*;

/** Used for key value pair. Put in place because javafx is not available in OpenJDK */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class Triplet<T1, T2, T3> implements Serializable {

  private T1 value1;

  private T2 value2;

  private T3 value3;
}
