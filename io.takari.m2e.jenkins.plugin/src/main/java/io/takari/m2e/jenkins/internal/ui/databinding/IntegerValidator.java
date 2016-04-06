package io.takari.m2e.jenkins.internal.ui.databinding;

import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.widgets.Control;

public class IntegerValidator extends DecoratedValidator {

  private String fieldName;
  private boolean mandatory;

  public IntegerValidator(Control control, String fieldName, boolean mandatory) {
    super(control);
    this.fieldName = fieldName;
    this.mandatory = mandatory;
  }

  @Override
  protected IStatus doValidate(Object value) {
    String strValue = value == null ? null : value.toString();

    if (strValue == null || strValue.trim().length() == 0) {
      if (mandatory) {
        return ValidationStatus.error("Must choose a " + fieldName);
      }
      return ValidationStatus.ok();
    }

    try {
      int val = Integer.valueOf(strValue).intValue();
      if (val <= 0) {
        return ValidationStatus.error("Invalid " + fieldName);
      }
    } catch (NumberFormatException e) {
      return ValidationStatus.error("Invalid " + fieldName);
    }
    return ValidationStatus.ok();
  }

}
