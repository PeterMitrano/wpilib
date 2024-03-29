package edu.wpi.first.wpilibj.hal;

public class SolenoidJNI extends JNIWrapper {
  public static native long initializeSolenoidPort(long portPointer);

  public static native long getPortWithModule(byte module, byte channel);

  public static native void setSolenoid(long port, boolean on);

  public static native boolean getSolenoid(long port);

  public static native int getPCMSolenoidBlackList(long pcm_pointer);

  public static native boolean getPCMSolenoidVoltageStickyFault(long pcm_pointer);

  public static native boolean getPCMSolenoidVoltageFault(long pcm_pointer);

  public static native void clearAllPCMStickyFaults(long pcm_pointer);
}
