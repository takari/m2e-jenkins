package io.takari.m2e.jenkins.internal.ui.databinding;

import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;

public abstract class DecoratedValidator implements IValidator {
  private ControlDecoration controlDecoration;

  protected DecoratedValidator(Control control) {
    this.controlDecoration = createControlDecoration(control);
  }

  public final IStatus validate(Object value) {

    IStatus status = doValidate(value);
    if (status != null && !status.isOK()) {
      controlDecoration.setDescriptionText(status.getMessage());
      controlDecoration.show();
    } else {
      controlDecoration.hide();
    }
    return status;
  }

  protected abstract IStatus doValidate(Object value);

  private ControlDecoration createControlDecoration(Control c) {
    ControlDecoration controlDecoration = new ControlDecoration(c, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration = FieldDecorationRegistry.getDefault()
        .getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    controlDecoration.setImage(fieldDecoration.getImage());

    return controlDecoration;
  }

}