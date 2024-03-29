package edu.wpi.first.wpilibj.hal;

public class RelayJNI extends DIOJNI {
  public static native void setRelayForward(long digital_port_pointer, boolean on);

  public static native void setRelayReverse(long digital_port_pointer, boolean on);

  public static native boolean getRelayForward(long digital_port_pointer);

  public static native boolean getRelayReverse(long digital_port_pointer);
}
