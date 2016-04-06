package io.takari.m2e.jenkins.internal.ui.databinding;

import java.beans.PropertyDescriptor;
import java.util.Set;

import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.observable.set.IObservableSet;
import org.eclipse.core.databinding.observable.set.ISetChangeListener;
import org.eclipse.core.databinding.observable.set.SetChangeEvent;
import org.eclipse.core.databinding.observable.value.AbstractObservableValue;
import org.eclipse.core.databinding.observable.value.ValueDiff;
import org.eclipse.core.internal.databinding.beans.BeanPropertyHelper;

@SuppressWarnings("restriction")
public class SetSelectionObservable extends AbstractObservableValue {
  private IObservableSet delegate;
  private Object element;

  public SetSelectionObservable(Object object, String property, Object element) {
    PropertyDescriptor propertyDescriptor = BeanPropertyHelper.getPropertyDescriptor(object.getClass(), property);
    Set<?> set = (Set<?>) BeanPropertyHelper.readProperty(object, propertyDescriptor);
    if (set instanceof IObservableSet) {
      delegate = (IObservableSet) set;
    } else {
      delegate = BeanProperties.set(property).observe(object);
      // BeansObservables.observeSet(object, property);
    }
    this.element = element;

    delegate.addSetChangeListener(new ISetChangeListener() {

      public void handleSetChange(SetChangeEvent event) {
        for (Object o : event.diff.getAdditions()) {
          if (o.equals(SetSelectionObservable.this.element)) {
            fireValueChange(new ValueDiff() {
              public Object getOldValue() {
                return true;
              }
              public Object getNewValue() {
                return false;
              }
            });
          }
        }

        for (Object o : event.diff.getRemovals()) {
          if (o.equals(SetSelectionObservable.this.element)) {
            fireValueChange(new ValueDiff() {
              public Object getOldValue() {
                return false;
              }
              public Object getNewValue() {
                return true;
              }
            });
          }
        }
      }
    });
  }

  public Object getValueType() {
    return Boolean.TYPE;
  }

  @Override
  protected Object doGetValue() {
    return delegate.contains(element);
  }

  @Override
  protected void doSetValue(Object value) {
    if (value == Boolean.TRUE) {
      delegate.add(element);
    } else {
      delegate.remove(element);
    }
  }

}
