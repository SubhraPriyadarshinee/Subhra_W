package com.walmart.move.nim.receiving.core.framework.transformer;

import com.walmart.move.nim.receiving.core.framework.observer.ApplicationCapability;
import java.util.List;

/**
 * *
 *
 * @author sitakant
 * @param <S> Source POJO
 * @param <D> Destination POJO
 */
public interface Transformer<S, D> extends ApplicationCapability {
  D transform(S s);

  List<D> transformList(List<S> s);

  S reverseTransform(D d);

  List<S> reverseTransformList(List<D> d);
}
