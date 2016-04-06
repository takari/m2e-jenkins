package io.takari.m2e.jenkins.internal.ui.databinding;

import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.conversion.NumberToStringConverter;

import com.ibm.icu.text.NumberFormat;

public class IntegerToStringConverter extends Converter {

  private final Converter delegate;

  public IntegerToStringConverter() {
    super(Integer.TYPE, String.class);
    NumberFormat nf = NumberFormat.getIntegerInstance();
    nf.setGroupingUsed(false);
    delegate = NumberToStringConverter.fromInteger(nf, true);
  }

  @Override
  public Object convert(Object fromObject) {
    return delegate.convert(fromObject);
  }

}
