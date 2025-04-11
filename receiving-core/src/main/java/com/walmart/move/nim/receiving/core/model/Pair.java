package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Used for key value pair. Put in place because javafx is not available in OpenJDK */
@Getter
@Setter
@AllArgsConstructor
public class Pair<K, V> implements Serializable {

  private K key;

  private V value;

  @Override
  public String toString() {
    return key + ", " + value;
  }

  @Override
  public int hashCode() {
    return key.hashCode() * 13 + (value == null ? 0 : value.hashCode());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof Pair) {
      Pair pair = (Pair) o;
      if (!Objects.equals(key, pair.key)) return false;
      return Objects.equals(value, pair.value);
    }
    return false;
  }
}
