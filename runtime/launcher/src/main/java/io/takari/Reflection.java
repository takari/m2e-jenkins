package io.takari;

public class Reflection {
  public static Class<?> getCallerClass(int n){
		StackTraceElement[] elements = new Throwable().getStackTrace();
		  return elements[n].getClass() ;
		}
}
