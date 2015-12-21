package com.davemorrissey.labs.subscaleview.decoder;

import android.support.annotation.NonNull;

/**
 * Compatibility factory to instantiate decoders with empty public constructors.
 * @param <T> The base type of the decoder this factory will produce.
 */
public class CompatDecoderFactory <T> implements DecoderFactory<T> {
  private Class<? extends T> clazz;

  public CompatDecoderFactory(@NonNull Class<? extends T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public T make() throws IllegalAccessException, InstantiationException {
    return clazz.newInstance();
  }
}
