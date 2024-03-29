package edu.wpi.first.wpilibj.hal;

public class DIOJNI extends JNIWrapper {
  public static native long initializeDigitalPort(long port_pointer);

  public static native boolean allocateDIO(long digital_port_pointer, boolean input);

  public static native void freeDIO(long digital_port_pointer);

  public static native void setDIO(long digital_port_pointer, short value);

  public static native boolean getDIO(long digital_port_pointer);

  public static native boolean getDIODirection(long digital_port_pointer);

  public static native void pulse(long digital_port_pointer, double pulseLength);

  public static native boolean isPulsing(long digital_port_pointer);

  public static native boolean isAnyPulsing();

  public static native short getLoopTiming();
}
