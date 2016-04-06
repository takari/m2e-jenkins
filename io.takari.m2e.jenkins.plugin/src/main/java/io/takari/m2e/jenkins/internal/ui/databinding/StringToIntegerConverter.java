package io.takari.m2e.jenkins.internal.ui.databinding;

import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.conversion.StringToNumberConverter;

public class StringToIntegerConverter extends Converter {

  private final Converter delegate;

  public StringToIntegerConverter() {
    super(String.class, Integer.TYPE);
    delegate = StringToNumberConverter.toInteger(true);
  }

  @Override
  public Object convert(Object fromObject) {
    return delegate.convert(fromObject);
  }

}
