package edu.wpi.first.wpilibj;

import edu.wpi.first.wpilibj.hal.CanTalonSRX;
import edu.wpi.first.wpilibj.hal.CanTalonJNI;
import edu.wpi.first.wpilibj.communication.UsageReporting;
import edu.wpi.first.wpilibj.communication.FRCNetworkCommunicationsLibrary.tResourceType;
import edu.wpi.first.wpilibj.hal.SWIGTYPE_p_double;
import edu.wpi.first.wpilibj.hal.SWIGTYPE_p_int;
import edu.wpi.first.wpilibj.hal.SWIGTYPE_p_CTR_Code;
import edu.wpi.first.wpilibj.tables.ITable;
import edu.wpi.first.wpilibj.tables.ITableListener;

public class CANTalon implements MotorSafety, PIDOutput, PIDSource, CANSpeedController {
  private MotorSafetyHelper m_safetyHelper;
  private boolean isInverted = false;
  protected PIDSourceType m_pidSource = PIDSourceType.kDisplacement;
  /**
   * Number of adc engineering units per 0 to 3.3V sweep.
   * This is necessary for scaling Analog Position in rotations/RPM.
   */
  private final int kNativeAdcUnitsPerRotation = 1024;
  /**
   * Number of pulse width engineering units per full rotation.
   * This is necessary for scaling Pulse Width Decoded Position in rotations/RPM.
   */
  private final double kNativePwdUnitsPerRotation = 4096.0;
  /**
   * Number of minutes per 100ms unit.  Useful for scaling velocities
   * measured by Talon's 100ms timebase to rotations per minute.
   */
  private final double kMinutesPer100msUnit = 1.0/600.0;

  public enum TalonControlMode implements CANSpeedController.ControlMode {
    PercentVbus(0), Position(1), Speed(2), Current(3), Voltage(4), Follower(5), Disabled(15);

    public final int value;

    public static TalonControlMode valueOf(int value) {
      for (TalonControlMode mode : values()) {
        if (mode.value == value) {
          return mode;
        }
      }

      return null;
    }

    private TalonControlMode(int value) {
      this.value = value;
    }

    @Override
    public boolean isPID() {
        return this == Current || this == Speed || this == Position;
    }

    @Override
    public int getValue() {
        return value;
    }
  }
  public enum FeedbackDevice {
    QuadEncoder(0), AnalogPot(2), AnalogEncoder(3), EncRising(4), EncFalling(5), CtreMagEncoder_Relative(6), CtreMagEncoder_Absolute(7), PulseWidth(8);

    public int value;

    public static FeedbackDevice valueOf(int value) {
      for (FeedbackDevice mode : values()) {
        if (mode.value == value) {
          return mode;
        }
      }

      return null;
    }

    private FeedbackDevice(int value) {
      this.value = value;
    }
  }
  /**
   * Depending on the sensor type, Talon can determine if sensor is plugged in ot not.
   */
  public enum FeedbackDeviceStatus {
    FeedbackStatusUnknown(0), FeedbackStatusPresent(1), FeedbackStatusNotPresent(2);
    public int value;
    public static FeedbackDeviceStatus valueOf(int value) {
      for (FeedbackDeviceStatus mode : values()) {
        if (mode.value == value) {
          return mode;
        }
      }
      return null;
    }
    private FeedbackDeviceStatus(int value) {
      this.value = value;
    }
  }
  /** enumerated types for frame rate ms */
  public enum StatusFrameRate {
    General(0), Feedback(1), QuadEncoder(2), AnalogTempVbat(3), PulseWidth(4);
    public int value;

    public static StatusFrameRate valueOf(int value) {
      for (StatusFrameRate mode : values()) {
        if (mode.value == value) {
          return mode;
        }
      }
      return null;
    }

    private StatusFrameRate(int value) {
      this.value = value;
    }
  }


  private CanTalonSRX m_impl;
  private TalonControlMode m_controlMode;
  private static double kDelayForSolicitedSignals = 0.004;
  private double m_minimumInput;
  private double m_maximumInput;

  int m_deviceNumber;
  boolean m_controlEnabled;
  int m_profile;

  double m_setPoint;
  /**
   * Encoder CPR, counts per rotations, also called codes per revoluion.
   * Default value of zero means the API behaves as it did during the 2015 season, each position
   * unit is a single pulse and there are four pulses per count (4X).
   * Caller can use configEncoderCodesPerRev to set the quadrature encoder CPR.
   */
  int m_codesPerRev;
  /**
   * Number of turns per rotation.  For example, a 10-turn pot spins ten full rotations from
   * a wiper voltage of zero to 3.3 volts.  Therefore knowing the
   * number of turns a full voltage sweep represents is necessary for calculating rotations
   * and velocity.
   * A default value of zero means the API behaves as it did during the 2015 season, there are 1024
   * position units from zero to 3.3V.
   */
  int m_numPotTurns;
  /**
   * Although the Talon handles feedback selection, caching the feedback selection is helpful at the API level
   * for scaling into rotations and RPM.
   */
  FeedbackDevice m_feedbackDevice;

  public CANTalon(int deviceNumber) {
    m_deviceNumber = deviceNumber;
    m_impl = new CanTalonSRX(deviceNumber);
    m_safetyHelper = new MotorSafetyHelper(this);
    m_controlEnabled = true;
    m_profile = 0;
    m_setPoint = 0;
    m_codesPerRev = 0;
    m_numPotTurns = 0;
    m_feedbackDevice = FeedbackDevice.QuadEncoder;
    setProfile(m_profile);
    applyControlMode(TalonControlMode.PercentVbus);
  }

  public CANTalon(int deviceNumber, int controlPeriodMs) {
    m_deviceNumber = deviceNumber;
    m_impl = new CanTalonSRX(deviceNumber, controlPeriodMs); /*
                                                              * bound period to
                                                              * be within [1
                                                              * ms,95 ms]
                                                              */
    m_safetyHelper = new MotorSafetyHelper(this);
    m_controlEnabled = true;
    m_profile = 0;
    m_setPoint = 0;
    m_codesPerRev = 0;
    m_numPotTurns = 0;
    m_feedbackDevice = FeedbackDevice.QuadEncoder;
    setProfile(m_profile);
    applyControlMode(TalonControlMode.PercentVbus);
  }

  @Override
  public void pidWrite(double output) {
    if (getControlMode() == TalonControlMode.PercentVbus) {
      set(output);
    } else {
      throw new IllegalStateException("PID only supported in PercentVbus mode");
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setPIDSourceType(PIDSourceType pidSource) {
    m_pidSource = pidSource;
  }

  /**
   * {@inheritDoc}
   */
  public PIDSourceType getPIDSourceType() {
    return m_pidSource;
  }

  @Override
  public double pidGet() {
    return getPosition();
  }

  public void delete() {
    disable();
    m_impl.delete();
  }

  /**
   * Sets the appropriate output on the talon, depending on the mode.
   *
   * In PercentVbus, the output is between -1.0 and 1.0, with 0.0 as stopped. In
   * Follower mode, the output is the integer device ID of the talon to
   * duplicate. In Voltage mode, outputValue is in volts. In Current mode,
   * outputValue is in amperes. In Speed mode, outputValue is in position change
   * / 10ms. In Position mode, outputValue is in encoder ticks or an analog
   * value, depending on the sensor.
   *
   * @param outputValue The setpoint value, as described above.
   */
  public void set(double outputValue) {
    /* feed safety helper since caller just updated our output */
    m_safetyHelper.feed();
    if (m_controlEnabled) {
      m_setPoint = outputValue; /* cache set point for getSetpoint() */
      switch (m_controlMode) {
        case PercentVbus:
          m_impl.Set(isInverted ? -outputValue : outputValue);
          break;
        case Follower:
          m_impl.SetDemand((int) outputValue);
          break;
        case Voltage:
          // Voltage is an 8.8 fixed point number.
          int volts = (int) ((isInverted ? -outputValue : outputValue) * 256);
          m_impl.SetDemand(volts);
          break;
        case Speed:
          m_impl.SetDemand(ScaleVelocityToNativeUnits(m_feedbackDevice,(isInverted ? -outputValue : outputValue)));
          break;
        case Position:
          m_impl.SetDemand(ScaleRotationsToNativeUnits(m_feedbackDevice,outputValue));
          break;
        case Current:
          double milliamperes = (isInverted ? -outputValue : outputValue) * 1000.0; /* mA*/
          m_impl.SetDemand((int)milliamperes);
          break;
        default:
          break;
      }
      m_impl.SetModeSelect(m_controlMode.value);
    }
  }

  /**
   * Inverts the direction of the motor's rotation. Only works in PercentVbus
   * mode.
   *
   * @param isInverted The state of inversion, true is inverted.
   */
  @Override
  public void setInverted(boolean isInverted) {
    this.isInverted = isInverted;
  }

  /**
   * Common interface for the inverting direction of a speed controller.
   *
   * @return isInverted The state of inversion, true is inverted.
   *
   */
  @Override
  public boolean getInverted() {
    return this.isInverted;
  }

  /**
   * Sets the output of the Talon.
   *
   * @param outputValue See set().
   * @param thisValueDoesNotDoAnything corresponds to syncGroup from Jaguar; not
   *        relevant here.
   */
  @Override
  public void set(double outputValue, byte thisValueDoesNotDoAnything) {
    set(outputValue);
  }

  /**
   * Resets the accumulated integral error and disables the controller.
   *
   * The only difference between this and {@link PIDController#reset} is that
   * the PIDController also resets the previous error for the D term, but the
   * difference should have minimal effect as it will only last one cycle.
   */
  public void reset() {
    disable();
    clearIAccum();
  }

  /**
   * Return true if Talon is enabled.
   *
   * @return true if the Talon is enabled and may be applying power to the motor
   */
  public boolean isEnabled() {
    return isControlEnabled();
  }

  /**
   * Returns the difference between the setpoint and the current position.
   *
   * @return The error in units corresponding to whichever mode we are in.
   * @see #set(double) set() for a detailed description of the various units.
   */
  public double getError() {
    return getClosedLoopError();
  }

  /**
   * Calls {@link #set(double)}.
   */
  public void setSetpoint(double setpoint) {
    set(setpoint);
  }

  /**
   * Flips the sign (multiplies by negative one) the sensor values going into
   * the talon.
   *
   * This only affects position and velocity closed loop control. Allows for
   * situations where you may have a sensor flipped and going in the wrong
   * direction.
   *
   * @param flip True if sensor input should be flipped; False if not.
   */
  public void reverseSensor(boolean flip) {
    m_impl.SetRevFeedbackSensor(flip ? 1 : 0);
  }

  /**
   * Flips the sign (multiplies by negative one) the throttle values going into
   * the motor on the talon in closed loop modes.
   *
   * @param flip True if motor output should be flipped; False if not.
   */
  public void reverseOutput(boolean flip) {
    m_impl.SetRevMotDuringCloseLoopEn(flip ? 1 : 0);
  }

  /**
   * Gets the current status of the Talon (usually a sensor value).
   *
   * In Current mode: returns output current. In Speed mode: returns current
   * speed. In Position mode: returns current sensor position. In PercentVbus
   * and Follower modes: returns current applied throttle.
   *
   * @return The current sensor value of the Talon.
   */
  public double get() {
    double retval = 0;
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    switch (m_controlMode) {
      case Voltage:
        retval = getOutputVoltage();
        break;
      case Current:
        retval = getOutputCurrent();
        break;
      case Speed:
        m_impl.GetSensorVelocity(swigp);
        retval = ScaleNativeUnitsToRpm(m_feedbackDevice,CanTalonJNI.intp_value(valuep));
        break;
      case Position:
        m_impl.GetSensorPosition(swigp);
        retval = ScaleNativeUnitsToRotations(m_feedbackDevice,CanTalonJNI.intp_value(valuep));
        break;
      case PercentVbus:
      default:
        m_impl.GetAppliedThrottle(swigp);
        retval = (double) CanTalonJNI.intp_value(valuep) / 1023.0;
        break;
    }
    return retval;
  }

  /**
   * Get the current encoder position, regardless of whether it is the current
   * feedback device.
   *
   * @return The current position of the encoder.
   */
  public int getEncPosition() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetEncPosition(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  public void setEncPosition(int newPosition) {
    setParameter(CanTalonSRX.param_t.eEncPosition, newPosition);
  }

  /**
   * Get the current encoder velocity, regardless of whether it is the current
   * feedback device.
   *
   * @return The current speed of the encoder.
   */
  public int getEncVelocity() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetEncVel(swigp);
    return CanTalonJNI.intp_value(valuep);
  }
  public int getPulseWidthPosition() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetPulseWidthPosition(swigp);
    return CanTalonJNI.intp_value(valuep);
  }
  public void setPulseWidthPosition(int newPosition) {
    setParameter(CanTalonSRX.param_t.ePwdPosition, newPosition);
  }
  public int getPulseWidthVelocity() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetPulseWidthVelocity(swigp);
    return CanTalonJNI.intp_value(valuep);
  }
  public int getPulseWidthRiseToFallUs() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetPulseWidthRiseToFallUs(swigp);
    return CanTalonJNI.intp_value(valuep);
  }
  public int getPulseWidthRiseToRiseUs() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetPulseWidthRiseToRiseUs(swigp);
    return CanTalonJNI.intp_value(valuep);
  }
  /**
   * @param which feedback sensor to check it if is connected.
   * @return status of caller's specified sensor type.
   */
  public FeedbackDeviceStatus isSensorPresent(FeedbackDevice feedbackDevice) {
    FeedbackDeviceStatus retval = FeedbackDeviceStatus.FeedbackStatusUnknown;
    /* detecting sensor health depends on which sensor caller cares about */
    switch(feedbackDevice){
      case QuadEncoder:
      case AnalogPot:
      case AnalogEncoder:
      case EncRising:
      case EncFalling:
        /* no real good way to tell if these sensor
          are actually present so return status unknown. */
        break;
      case PulseWidth:
      case CtreMagEncoder_Relative:
      case CtreMagEncoder_Absolute:
        /* all of these require pulse width signal to be present. */
        long valuep = CanTalonJNI.new_intp();
        SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
        SWIGTYPE_p_CTR_Code status = m_impl.IsPulseWidthSensorPresent(swigp);
        /* TODO: add a check for CanTalonJNI.CTR_Codep_value(status) */
        if( CanTalonJNI.intp_value(valuep) == 0 ){
          /* Talon not getting a signal */
          retval = FeedbackDeviceStatus.FeedbackStatusNotPresent;
        }else{
          /* getting good signal */
          retval = FeedbackDeviceStatus.FeedbackStatusPresent;
        }
        break;
    }
    return retval;
  }
  /**
   * Get the number of of rising edges seen on the index pin.
   *
   * @return number of rising edges on idx pin.
   */
  public int getNumberOfQuadIdxRises() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetEncIndexRiseEvents(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  /**
   * @return IO level of QUADA pin.
   */
  public int getPinStateQuadA() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetQuadApin(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  /**
   * @return IO level of QUADB pin.
   */
  public int getPinStateQuadB() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetQuadBpin(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  /**
   * @return IO level of QUAD Index pin.
   */
  public int getPinStateQuadIdx() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetQuadIdxpin(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  public void setAnalogPosition(int newPosition){
    setParameter(CanTalonSRX.param_t.eAinPosition, (double)newPosition);
  }
  /**
   * Get the current analog in position, regardless of whether it is the current
   * feedback device.
   *
   * @return The 24bit analog position. The bottom ten bits is the ADC (0 -
   *         1023) on the analog pin of the Talon. The upper 14 bits tracks the
   *         overflows and underflows (continuous sensor).
   */
  public int getAnalogInPosition() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetAnalogInWithOv(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  /**
   * Get the current analog in position, regardless of whether it is the current
   * feedback device.
   *$
   * @return The ADC (0 - 1023) on analog pin of the Talon.
   */
  public int getAnalogInRaw() {
    return getAnalogInPosition() & 0x3FF;
  }

  /**
   * Get the current encoder velocity, regardless of whether it is the current
   * feedback device.
   *
   * @return The current speed of the analog in device.
   */
  public int getAnalogInVelocity() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetAnalogInVel(swigp);
    return CanTalonJNI.intp_value(valuep);
  }

  /**
   * Get the current difference between the setpoint and the sensor value.
   *
   * @return The error, in whatever units are appropriate.
   */
  public int getClosedLoopError() {
    long valuep = CanTalonJNI.new_intp();
    /* retrieve the closed loop error in native units */
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetCloseLoopErr(swigp);
    return CanTalonJNI.intp_value(valuep);
  }
  /**
   * Set the allowable closed loop error.
   * @param allowableCloseLoopError allowable closed loop error for selected profile.
   * 			mA for Curent closed loop.
   * 			Talon Native Units for position and velocity.
   */
  public void setAllowableClosedLoopErr(int allowableCloseLoopError)
  {
    if(m_profile == 0){
	  setParameter(CanTalonSRX.param_t.eProfileParamSlot0_AllowableClosedLoopErr, (double)allowableCloseLoopError);
    }else{
	  setParameter(CanTalonSRX.param_t.eProfileParamSlot1_AllowableClosedLoopErr, (double)allowableCloseLoopError);
    }
  }

  // Returns true if limit switch is closed. false if open.
  public boolean isFwdLimitSwitchClosed() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetLimitSwitchClosedFor(swigp);
    return (CanTalonJNI.intp_value(valuep) == 0) ? true : false;
  }

  // Returns true if limit switch is closed. false if open.
  public boolean isRevLimitSwitchClosed() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetLimitSwitchClosedRev(swigp);
    return (CanTalonJNI.intp_value(valuep) == 0) ? true : false;
  }

  // Returns true if break is enabled during neutral. false if coast.
  public boolean getBrakeEnableDuringNeutral() {
    long valuep = CanTalonJNI.new_intp();
    SWIGTYPE_p_int swigp = new SWIGTYPE_p_int(valuep, true);
    m_impl.GetBrakeIsEnabled(swigp);
    return (CanTalonJNI.intp_value(valuep) == 0) ? false : true;
  }

  /**
   * Configure how many codes per revolution are generated by your encoder.
   *
   * @param codesPerRev The number of counts per revolution.
   */
  public void configEncoderCodesPerRev(int codesPerRev) {
    /* first save the scalar so that all getters/setter work as the user expects */
    m_codesPerRev = codesPerRev;
    /* next send the scalar to the Talon over CAN.  This is so that the Talon can report
      it to whoever needs it, like the webdash.  Don't bother checking the return,
      this is only for instrumentation and is not necessary for Talon functionality. */
    setParameter(CanTalonSRX.param_t.eNumberEncoderCPR, m_codesPerRev);
  }
  /**
   * Configure the number of turns on the potentiometer.
   *
   * @param turns The number of turns of the potentiometer.
   */
  public void configPotentiometerTurns(int turns) {
    /* first save the scalar so that all getters/setter work as the user expects */
    m_numPotTurns = turns;
    /* next send the scalar to the Talon over CAN.  This is so that the Talon can report
      it to whoever needs it, like the webdash.  Don't bother checking the return,
      this is only for instrumentation and is not necessary for Talon functionality. */
    setParameter(CanTalonSRX.param_t.eNumberPotTurns, m_numPotTurns);
  }
  /**
   * Returns temperature of Talon, in degrees Celsius.
   */
  public double getTemperature() {
    long tempp = CanTalonJNI.new_doublep(); // Create a new swig pointer.
    m_impl.GetTemp(new SWIGTYPE_p_double(tempp, true));
    return CanTalonJNI.doublep_value(tempp);
  }

  /**
   * Returns the current going through the Talon, in Amperes.
   */
  public double getOutputCurrent() {
    long curp = CanTalonJNI.new_doublep(); // Create a new swig pointer.
    m_impl.GetCurrent(new SWIGTYPE_p_double(curp, true));
    return CanTalonJNI.doublep_value(curp);
  }

  /**
   * @return The voltage being output by the Talon, in Volts.
   */
  public double getOutputVoltage() {
    long throttlep = CanTalonJNI.new_intp();
    m_impl.GetAppliedThrottle(new SWIGTYPE_p_int(throttlep, true));
    double voltage = getBusVoltage() * (double) CanTalonJNI.intp_value(throttlep) / 1023.0;
    return voltage;
  }

  /**
   * @return The voltage at the battery terminals of the Talon, in Volts.
   */
  public double getBusVoltage() {
    long voltagep = CanTalonJNI.new_doublep();
    SWIGTYPE_p_CTR_Code status = m_impl.GetBatteryV(new SWIGTYPE_p_double(voltagep, true));
    /*
     * Note: This section needs the JNI bindings regenerated with
     * pointer_functions for CTR_Code included in order to be able to catch
     * notice and throw errors. if (CanTalonJNI.CTR_Codep_value(status) != 0) {
     * // TODO throw an error. }
     */

    return CanTalonJNI.doublep_value(voltagep);
  }

  /**
   * TODO documentation (see CANJaguar.java)
   *
   * @return The position of the sensor currently providing feedback. When using
   *         analog sensors, 0 units corresponds to 0V, 1023 units corresponds
   *         to 3.3V When using an analog encoder (wrapping around 1023 to 0 is
   *         possible) the units are still 3.3V per 1023 units. When using
   *         quadrature, each unit is a quadrature edge (4X) mode.
   */
  public double getPosition() {
    long positionp = CanTalonJNI.new_intp();
    m_impl.GetSensorPosition(new SWIGTYPE_p_int(positionp, true));
    return ScaleNativeUnitsToRotations(m_feedbackDevice,CanTalonJNI.intp_value(positionp));
  }

  public void setPosition(double pos) {
    int nativePos = ScaleRotationsToNativeUnits(m_feedbackDevice,pos);
    m_impl.SetSensorPosition(nativePos);
  }

  /**
   * TODO documentation (see CANJaguar.java)
   *
   * @return The speed of the sensor currently providing feedback.
   *
   *         The speed units will be in the sensor's native ticks per 100ms.
   *
   *         For analog sensors, 3.3V corresponds to 1023 units. So a speed of
   *         200 equates to ~0.645 dV per 100ms or 6.451 dV per second. If this
   *         is an analog encoder, that likely means 1.9548 rotations per sec.
   *         For quadrature encoders, each unit corresponds a quadrature edge
   *         (4X). So a 250 count encoder will produce 1000 edge events per
   *         rotation. An example speed of 200 would then equate to 20% of a
   *         rotation per 100ms, or 10 rotations per second.
   */
  public double getSpeed() {
    long speedp = CanTalonJNI.new_intp();
    m_impl.GetSensorVelocity(new SWIGTYPE_p_int(speedp, true));
    return ScaleNativeUnitsToRpm(m_feedbackDevice,CanTalonJNI.intp_value(speedp));
  }

  public TalonControlMode getControlMode() {
    return m_controlMode;
  }

  public void setControlMode(int mode) {
    TalonControlMode tcm = TalonControlMode.valueOf(mode);
    if(tcm != null)
      changeControlMode(tcm);
  }

  /**
   * Fixup the m_controlMode so set() serializes the correct demand value. Also
   * fills the modeSelecet in the control frame to disabled.
   *$
   * @param controlMode Control mode to ultimately enter once user calls set().
   * @see #set
   */
  private void applyControlMode(TalonControlMode controlMode) {
    m_controlMode = controlMode;
    if (controlMode == TalonControlMode.Disabled)
      m_controlEnabled = false;
    // Disable until set() is called.
    m_impl.SetModeSelect(TalonControlMode.Disabled.value);

    UsageReporting.report(tResourceType.kResourceType_CANTalonSRX, m_deviceNumber + 1,
        controlMode.value);
  }

  public void changeControlMode(TalonControlMode controlMode) {
    if (m_controlMode == controlMode) {
      /* we already are in this mode, don't perform disable workaround */
    } else {
      applyControlMode(controlMode);
    }
  }

  public void setFeedbackDevice(FeedbackDevice device) {
    /* save the selection so that future setters/getters know which scalars to apply */
    m_feedbackDevice = device;
    /* pass feedback to actual CAN frame */
    m_impl.SetFeedbackDeviceSelect(device.value);
  }

  public void setStatusFrameRateMs(StatusFrameRate stateFrame, int periodMs) {
    m_impl.SetStatusFrameRate(stateFrame.value, periodMs);
  }

  public void enableControl() {
    changeControlMode(m_controlMode);
    m_controlEnabled = true;
  }

  public void enable() {
    enableControl();
  }

  public void disableControl() {
    m_impl.SetModeSelect(TalonControlMode.Disabled.value);
    m_controlEnabled = false;
  }

  public boolean isControlEnabled() {
    return m_controlEnabled;
  }

  /**
   * Get the current proportional constant.
   *
   * @return double proportional constant for current profile.
   */
  public double getP() {
    // if(!(m_controlMode.equals(ControlMode.Position) ||
    // m_controlMode.equals(ControlMode.Speed))) {
    // throw new
    // IllegalStateException("PID mode only applies in Position and Speed modes.");
    // }

    // Update the information that we have.
    if (m_profile == 0)
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot0_P);
    else
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot1_P);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long pp = CanTalonJNI.new_doublep();
    m_impl.GetPgain(m_profile, new SWIGTYPE_p_double(pp, true));
    return CanTalonJNI.doublep_value(pp);
  }

  public double getI() {
    // if(!(m_controlMode.equals(ControlMode.Position) ||
    // m_controlMode.equals(ControlMode.Speed))) {
    // throw new
    // IllegalStateException("PID mode only applies in Position and Speed modes.");
    // }

    // Update the information that we have.
    if (m_profile == 0)
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot0_I);
    else
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot1_I);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long ip = CanTalonJNI.new_doublep();
    m_impl.GetIgain(m_profile, new SWIGTYPE_p_double(ip, true));
    return CanTalonJNI.doublep_value(ip);
  }

  public double getD() {
    // if(!(m_controlMode.equals(ControlMode.Position) ||
    // m_controlMode.equals(ControlMode.Speed))) {
    // throw new
    // IllegalStateException("PID mode only applies in Position and Speed modes.");
    // }

    // Update the information that we have.
    if (m_profile == 0)
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot0_D);
    else
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot1_D);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long dp = CanTalonJNI.new_doublep();
    m_impl.GetDgain(m_profile, new SWIGTYPE_p_double(dp, true));
    return CanTalonJNI.doublep_value(dp);
  }

  public double getF() {
    // if(!(m_controlMode.equals(ControlMode.Position) ||
    // m_controlMode.equals(ControlMode.Speed))) {
    // throw new
    // IllegalStateException("PID mode only applies in Position and Speed modes.");
    // }

    // Update the information that we have.
    if (m_profile == 0)
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot0_F);
    else
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot1_F);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long fp = CanTalonJNI.new_doublep();
    m_impl.GetFgain(m_profile, new SWIGTYPE_p_double(fp, true));
    return CanTalonJNI.doublep_value(fp);
  }

  public double getIZone() {
    // if(!(m_controlMode.equals(ControlMode.Position) ||
    // m_controlMode.equals(ControlMode.Speed))) {
    // throw new
    // IllegalStateException("PID mode only applies in Position and Speed modes.");
    // }

    // Update the information that we have.
    if (m_profile == 0)
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot0_IZone);
    else
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot1_IZone);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long fp = CanTalonJNI.new_intp();
    m_impl.GetIzone(m_profile, new SWIGTYPE_p_int(fp, true));
    return CanTalonJNI.intp_value(fp);
  }

  /**
   * Get the closed loop ramp rate for the current profile.
   *
   * Limits the rate at which the throttle will change. Only affects position
   * and speed closed loop modes.
   *
   * @return rampRate Maximum change in voltage, in volts / sec.
   * @see #setProfile For selecting a certain profile.
   */
  public double getCloseLoopRampRate() {
    // if(!(m_controlMode.equals(ControlMode.Position) ||
    // m_controlMode.equals(ControlMode.Speed))) {
    // throw new
    // IllegalStateException("PID mode only applies in Position and Speed modes.");
    // }

    // Update the information that we have.
    if (m_profile == 0)
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot0_CloseLoopRampRate);
    else
      m_impl.RequestParam(CanTalonSRX.param_t.eProfileParamSlot1_CloseLoopRampRate);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long fp = CanTalonJNI.new_intp();
    m_impl.GetCloseLoopRampRate(m_profile, new SWIGTYPE_p_int(fp, true));
    double throttlePerMs = CanTalonJNI.intp_value(fp);
    return throttlePerMs / 1023.0 * 12.0 * 1000.0;
  }

  /**
   * @return The version of the firmware running on the Talon
   */
  public long GetFirmwareVersion() {

    // Update the information that we have.
    m_impl.RequestParam(CanTalonSRX.param_t.eFirmVers);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long fp = CanTalonJNI.new_intp();
    m_impl.GetParamResponseInt32(CanTalonSRX.param_t.eFirmVers, new SWIGTYPE_p_int(fp, true));
    return CanTalonJNI.intp_value(fp);
  }

  public long GetIaccum() {

    // Update the information that we have.
    m_impl.RequestParam(CanTalonSRX.param_t.ePidIaccum);

    // Briefly wait for new values from the Talon.
    Timer.delay(kDelayForSolicitedSignals);

    long fp = CanTalonJNI.new_intp();
    m_impl.GetParamResponseInt32(CanTalonSRX.param_t.ePidIaccum, new SWIGTYPE_p_int(fp, true));
    return CanTalonJNI.intp_value(fp);
  }

  /**
   * Set the proportional value of the currently selected profile.
   *
   * @param p Proportional constant for the currently selected PID profile.
   * @see #setProfile For selecting a certain profile.
   */
  public void setP(double p) {
    m_impl.SetPgain(m_profile, p);
  }

  /**
   * Set the integration constant of the currently selected profile.
   *
   * @param i Integration constant for the currently selected PID profile.
   * @see #setProfile For selecting a certain profile.
   */
  public void setI(double i) {
    m_impl.SetIgain(m_profile, i);
  }

  /**
   * Set the derivative constant of the currently selected profile.
   *
   * @param d Derivative constant for the currently selected PID profile.
   * @see #setProfile For selecting a certain profile.
   */
  public void setD(double d) {
    m_impl.SetDgain(m_profile, d);
  }

  /**
   * Set the feedforward value of the currently selected profile.
   *
   * @param f Feedforward constant for the currently selected PID profile.
   * @see #setProfile For selecting a certain profile.
   */
  public void setF(double f) {
    m_impl.SetFgain(m_profile, f);
  }

  /**
   * Set the integration zone of the current Closed Loop profile.
   *
   * Whenever the error is larger than the izone value, the accumulated
   * integration error is cleared so that high errors aren't racked up when at
   * high errors. An izone value of 0 means no difference from a standard PIDF
   * loop.
   *
   * @param izone Width of the integration zone.
   * @see #setProfile For selecting a certain profile.
   */
  public void setIZone(int izone) {
    m_impl.SetIzone(m_profile, izone);
  }

  /**
   * Set the closed loop ramp rate for the current profile.
   *
   * Limits the rate at which the throttle will change. Only affects position
   * and speed closed loop modes.
   *
   * @param rampRate Maximum change in voltage, in volts / sec.
   * @see #setProfile For selecting a certain profile.
   */
  public void setCloseLoopRampRate(double rampRate) {
    // CanTalonSRX takes units of Throttle (0 - 1023) / 1ms.
    int rate = (int) (rampRate * 1023.0 / 12.0 / 1000.0);
    m_impl.SetCloseLoopRampRate(m_profile, rate);
  }

  /**
   * Set the voltage ramp rate for the current profile.
   *
   * Limits the rate at which the throttle will change. Affects all modes.
   *
   * @param rampRate Maximum change in voltage, in volts / sec.
   */
  public void setVoltageRampRate(double rampRate) {
    // CanTalonSRX takes units of Throttle (0 - 1023) / 10ms.
    int rate = (int) (rampRate * 1023.0 / 12.0 / 100.0);
    m_impl.SetRampThrottle(rate);
  }

  public void setVoltageCompensationRampRate(double rampRate) {
    m_impl.SetVoltageCompensationRate(rampRate / 1000);
  }
  /**
   * Clear the accumulator for I gain.
   */
  public void ClearIaccum() {
    SWIGTYPE_p_CTR_Code status = m_impl.SetParam(CanTalonSRX.param_t.ePidIaccum, 0);
  }

  /**
   * Sets control values for closed loop control.
   *
   * @param p Proportional constant.
   * @param i Integration constant.
   * @param d Differential constant.
   * @param f Feedforward constant.
   * @param izone Integration zone -- prevents accumulation of integration error
   *        with large errors. Setting this to zero will ignore any izone stuff.
   * @param closeLoopRampRate Closed loop ramp rate. Maximum change in voltage,
   *        in volts / sec.
   * @param profile which profile to set the pid constants for. You can have two
   *        profiles, with values of 0 or 1, allowing you to keep a second set
   *        of values on hand in the talon. In order to switch profiles without
   *        recalling setPID, you must call setProfile().
   */
  public void setPID(double p, double i, double d, double f, int izone, double closeLoopRampRate,
      int profile) {
    if (profile != 0 && profile != 1)
      throw new IllegalArgumentException("Talon PID profile must be 0 or 1.");
    m_profile = profile;
    setProfile(profile);
    setP(p);
    setI(i);
    setD(d);
    setF(f);
    setIZone(izone);
    setCloseLoopRampRate(closeLoopRampRate);
  }

  public void setPID(double p, double i, double d) {
    setPID(p, i, d, 0, 0, 0, m_profile);
  }

  /**
   * @return The latest value set using set().
   */
  public double getSetpoint() {
    return m_setPoint;
  }

  /**
   * Select which closed loop profile to use, and uses whatever PIDF gains and
   * the such that are already there.
   */
  public void setProfile(int profile) {
    if (profile != 0 && profile != 1)
      throw new IllegalArgumentException("Talon PID profile must be 0 or 1.");
    m_profile = profile;
    m_impl.SetProfileSlotSelect(m_profile);
  }

  /**
   * Common interface for stopping a motor.
   *
   * @deprecated Use disableControl instead.
   */
  @Override
  @Deprecated
  public void stopMotor() {
    disableControl();
  }

  @Override
  public void disable() {
    disableControl();
  }

  public int getDeviceID() {
    return m_deviceNumber;
  }

  // TODO: Documentation for all these accessors/setters for misc. stuff.
  public void clearIAccum() {
    SWIGTYPE_p_CTR_Code status = m_impl.SetParam(CanTalonSRX.param_t.ePidIaccum, 0);
  }

  public void setForwardSoftLimit(double forwardLimit) {
    int nativeLimitPos = ScaleRotationsToNativeUnits(m_feedbackDevice,forwardLimit);
    m_impl.SetForwardSoftLimit(nativeLimitPos);
  }

  public int getForwardSoftLimit() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetForwardSoftLimit(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public void enableForwardSoftLimit(boolean enable) {
    m_impl.SetForwardSoftEnable(enable ? 1 : 0);
  }

  public boolean isForwardSoftLimitEnabled() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetForwardSoftEnable(new SWIGTYPE_p_int(valuep, true));
    return (CanTalonJNI.intp_value(valuep) == 0) ? false : true;
  }

  public void setReverseSoftLimit(double reverseLimit) {
    int nativeLimitPos = ScaleRotationsToNativeUnits(m_feedbackDevice,reverseLimit);
    m_impl.SetReverseSoftLimit(nativeLimitPos);
  }

  public int getReverseSoftLimit() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetReverseSoftLimit(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public void enableReverseSoftLimit(boolean enable) {
    m_impl.SetReverseSoftEnable(enable ? 1 : 0);
  }

  public boolean isReverseSoftLimitEnabled() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetReverseSoftEnable(new SWIGTYPE_p_int(valuep, true));
    return (CanTalonJNI.intp_value(valuep) == 0) ? false : true;
  }
  /**
   * Configure the maximum voltage that the Jaguar will ever output.
   *
   * This can be used to limit the maximum output voltage in all modes so that
   * motors which cannot withstand full bus voltage can be used safely.
   *
   * @param voltage The maximum voltage output by the Jaguar.
   */
  public void configMaxOutputVoltage(double voltage) {
    /* config peak throttle when in closed-loop mode in the fwd and rev direction. */
  	configPeakOutputVoltage(voltage, -voltage);
  }

  public void configPeakOutputVoltage(double forwardVoltage,double reverseVoltage) {
    /* bounds checking */
    if(forwardVoltage > 12)
  	  forwardVoltage = 12;
    else if(forwardVoltage < 0)
      forwardVoltage = 0;
    if(reverseVoltage > 0)
  	  reverseVoltage = 0;
    else if(reverseVoltage < -12)
      reverseVoltage = -12;
    /* config calls */
    setParameter(CanTalonSRX.param_t.ePeakPosOutput,1023*forwardVoltage/12.0);
    setParameter(CanTalonSRX.param_t.ePeakNegOutput,1023*reverseVoltage/12.0);
  }
  public void configNominalOutputVoltage(double forwardVoltage,double reverseVoltage) {
    /* bounds checking */
    if(forwardVoltage > 12)
  	  forwardVoltage = 12;
    else if(forwardVoltage < 0)
      forwardVoltage = 0;
    if(reverseVoltage > 0)
  	  reverseVoltage = 0;
    else if(reverseVoltage < -12)
      reverseVoltage = -12;
    /* config calls */
    setParameter(CanTalonSRX.param_t.eNominalPosOutput,1023*forwardVoltage/12.0);
    setParameter(CanTalonSRX.param_t.eNominalNegOutput,1023*reverseVoltage/12.0);
  }
  /**
   * General set frame.  Since the parameter is a general integral type, this can
   * be used for testing future features.
   */
  public void setParameter(CanTalonSRX.param_t paramEnum, double value){
    SWIGTYPE_p_CTR_Code status = m_impl.SetParam(paramEnum,value);
    /* TODO: error report to driver station */
  }
  /**
   * General get frame.  Since the parameter is a general integral type, this can
   * be used for testing future features.
   */
  public double getParameter(CanTalonSRX.param_t paramEnum) {
	/* transmit a request for this param */
    m_impl.RequestParam(paramEnum);
    /* Briefly wait for new values from the Talon. */
    Timer.delay(kDelayForSolicitedSignals);
	/* poll out latest response value */
    long pp = CanTalonJNI.new_doublep();
    SWIGTYPE_p_CTR_Code status = m_impl.GetParamResponse(paramEnum, new SWIGTYPE_p_double(pp, true));
	/* pass latest value back to caller */
    return CanTalonJNI.doublep_value(pp);
  }
  public void clearStickyFaults() {
    m_impl.ClearStickyFaults();
  }

  public void enableLimitSwitch(boolean forward, boolean reverse) {
    int mask = 4 + (forward ? 1 : 0) * 2 + (reverse ? 1 : 0);
    m_impl.SetOverrideLimitSwitchEn(mask);
  }

  /**
   * Configure the fwd limit switch to be normally open or normally closed.
   * Talon will disable momentarilly if the Talon's current setting is
   * dissimilar to the caller's requested setting.
   *
   * Since Talon saves setting to flash this should only affect a given Talon
   * initially during robot install.
   *
   * @param normallyOpen true for normally open. false for normally closed.
   */
  public void ConfigFwdLimitSwitchNormallyOpen(boolean normallyOpen) {
    SWIGTYPE_p_CTR_Code status =
        m_impl.SetParam(CanTalonSRX.param_t.eOnBoot_LimitSwitch_Forward_NormallyClosed,
            normallyOpen ? 0 : 1);
  }

  /**
   * Configure the rev limit switch to be normally open or normally closed.
   * Talon will disable momentarilly if the Talon's current setting is
   * dissimilar to the caller's requested setting.
   *
   * Since Talon saves setting to flash this should only affect a given Talon
   * initially during robot install.
   *
   * @param normallyOpen true for normally open. false for normally closed.
   */
  public void ConfigRevLimitSwitchNormallyOpen(boolean normallyOpen) {
    SWIGTYPE_p_CTR_Code status =
        m_impl.SetParam(CanTalonSRX.param_t.eOnBoot_LimitSwitch_Reverse_NormallyClosed,
            normallyOpen ? 0 : 1);
  }

  public void enableBrakeMode(boolean brake) {
    m_impl.SetOverrideBrakeType(brake ? 2 : 1);
  }

  public int getFaultOverTemp() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_OverTemp(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getFaultUnderVoltage() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_UnderVoltage(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getFaultForLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_ForLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getFaultRevLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_RevLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getFaultHardwareFailure() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_HardwareFailure(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getFaultForSoftLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_ForSoftLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getFaultRevSoftLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetFault_RevSoftLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getStickyFaultOverTemp() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetStckyFault_OverTemp(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getStickyFaultUnderVoltage() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetStckyFault_UnderVoltage(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getStickyFaultForLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetStckyFault_ForLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getStickyFaultRevLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetStckyFault_RevLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getStickyFaultForSoftLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetStckyFault_ForSoftLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }

  public int getStickyFaultRevSoftLim() {
    long valuep = CanTalonJNI.new_intp();
    m_impl.GetStckyFault_RevSoftLim(new SWIGTYPE_p_int(valuep, true));
    return CanTalonJNI.intp_value(valuep);
  }
  /**
   * @return Number of native units per rotation if scaling info is available.
   *  			Zero if scaling information is not available.
   */
  double GetNativeUnitsPerRotationScalar(FeedbackDevice devToLookup)
  {
	  double retval = 0;
	  boolean scalingAvail = false;
	  switch(devToLookup){
		  case QuadEncoder:
		  {	/* When caller wants to lookup Quadrature, the QEI may be in 1x if the selected feedback is edge counter.
			 * Additionally if the quadrature source is the CTRE Mag encoder, then the CPR is known.
			 * This is nice in that the calling app does not require knowing the CPR at all.
			 * So do both checks here.
			 */
			 int qeiPulsePerCount = 4; /* default to 4x */
			 switch(m_feedbackDevice){
				case CtreMagEncoder_Relative:
				case CtreMagEncoder_Absolute:
					/* we assume the quadrature signal comes from the MagEnc,
						of which we know the CPR already */
					retval = kNativePwdUnitsPerRotation;
					scalingAvail = true;
					break;
				case EncRising: /* Talon's QEI is setup for 1x, so perform 1x math */
				case EncFalling:
					qeiPulsePerCount = 1;
					break;
				case QuadEncoder: /* Talon's QEI is 4x */
				default: /* pulse width and everything else, assume its regular quad use. */
					break;
			}
			if(scalingAvail){
				/* already deduced the scalar above, we're done. */
			}else{
				/* we couldn't deduce the scalar just based on the selection */
			  if(0 == m_codesPerRev){
				  /* caller has never set the CPR.  Most likely caller
					  is just using engineering units so fall to the
					  bottom of this func.*/
			  }else{
				  /* Talon expects PPR units */
				  retval = 4 * m_codesPerRev;
				  scalingAvail = true;
			  }
			}
		  }	break;
		  case EncRising:
		  case EncFalling:
			  if(0 == m_codesPerRev){
				  /* caller has never set the CPR.  Most likely caller
					  is just using engineering units so fall to the
					  bottom of this func.*/
			  }else{
  				  /* Talon expects PPR units */
				  retval = 1 * m_codesPerRev;
  				  scalingAvail = true;
			  }
			  break;
		  case AnalogPot:
		  case AnalogEncoder:
			  if(0 == m_numPotTurns){
				  /* caller has never set the CPR.  Most likely caller
  					is just using engineering units so fall to the
  					bottom of this func.*/
			  }else {
				  retval = (double)kNativeAdcUnitsPerRotation / m_numPotTurns;
			  	  scalingAvail = true;
  			  }
			  break;
		  case CtreMagEncoder_Relative:
		  case CtreMagEncoder_Absolute:
		  case PulseWidth:
			  retval = kNativePwdUnitsPerRotation;
			  scalingAvail = true;
			  break;
	  }
	  /* if scaling info is not available give caller zero */
	  if(false == scalingAvail)
		return 0;
	  return retval;
  }
  /**
   * @param fullRotations 	double precision value representing number of rotations of selected feedback sensor.
   *							If user has never called the config routine for the selected sensor, then the caller
   *							is likely passing rotations in engineering units already, in which case it is returned
   *							as is.
   *							@see configPotentiometerTurns
   *							@see configEncoderCodesPerRev
   * @return fullRotations in native engineering units of the Talon SRX firmware.
   */
  int ScaleRotationsToNativeUnits(FeedbackDevice devToLookup, double fullRotations)
  {
	  /* first assume we don't have config info, prep the default return */
	  int retval = (int)fullRotations;
	  /* retrieve scaling info */
	  double scalar = GetNativeUnitsPerRotationScalar(devToLookup);
	  /* apply scalar if its available */
	  if(scalar > 0)
		  retval = (int)(fullRotations*scalar);
	  return retval;
  }
  /**
   * @param rpm 	double precision value representing number of rotations per minute of selected feedback sensor.
   *							If user has never called the config routine for the selected sensor, then the caller
   *							is likely passing rotations in engineering units already, in which case it is returned
   *							as is.
   *							@see configPotentiometerTurns
   *							@see configEncoderCodesPerRev
   * @return sensor velocity in native engineering units of the Talon SRX firmware.
   */
  int ScaleVelocityToNativeUnits(FeedbackDevice devToLookup, double rpm)
  {
	  /* first assume we don't have config info, prep the default return */
	  int retval = (int)rpm;
  	  /* retrieve scaling info */
	  double scalar = GetNativeUnitsPerRotationScalar(devToLookup);
	  /* apply scalar if its available */
	  if(scalar > 0)
  		retval = (int)(rpm * kMinutesPer100msUnit * scalar);
	  return retval;
  }
  /**
   * @param nativePos 	integral position of the feedback sensor in native Talon SRX units.
   *							If user has never called the config routine for the selected sensor, then the return
   *							will be in TALON SRX units as well to match the behavior in the 2015 season.
   *							@see configPotentiometerTurns
   *							@see configEncoderCodesPerRev
   * @return double precision number of rotations, unless config was never performed.
   */
  double ScaleNativeUnitsToRotations(FeedbackDevice devToLookup, int nativePos)
  {
	  /* first assume we don't have config info, prep the default return */
	  double retval = (double)nativePos;
	  /* retrieve scaling info */
	  double scalar = GetNativeUnitsPerRotationScalar(devToLookup);
	  /* apply scalar if its available */
	  if(scalar > 0)
		  retval = ((double)nativePos) / scalar;
	  return retval;
  }
  /**
   * @param nativeVel 	integral velocity of the feedback sensor in native Talon SRX units.
   *							If user has never called the config routine for the selected sensor, then the return
   *							will be in TALON SRX units as well to match the behavior in the 2015 season.
   *							@see configPotentiometerTurns
   *							@see configEncoderCodesPerRev
   * @return double precision of sensor velocity in RPM, unless config was never performed.
   */
  double ScaleNativeUnitsToRpm(FeedbackDevice devToLookup, long nativeVel)
  {
    /* first assume we don't have config info, prep the default return */
	double retval = (double)nativeVel;
	/* retrieve scaling info */
	double scalar = GetNativeUnitsPerRotationScalar(devToLookup);
	/* apply scalar if its available */
	if(scalar > 0)
  	  retval = (double)(nativeVel) / (scalar*kMinutesPer100msUnit);
  	return retval;
  }

  /**
   * Enables Talon SRX to automatically zero the Sensor Position whenever an
   * edge is detected on the index signal. 
   * @param enable 		boolean input, pass true to enable feature or false to disable.
   * @param risingEdge 	boolean input, pass true to clear the position on rising edge,
   *					pass false to clear the position on falling edge.
   */
  public void enableZeroSensorPositionOnIndex(boolean enable, boolean risingEdge) {
    if(enable){
	  /* enable the feature, update the edge polarity first to ensure
		it is correct before the feature is enabled. */
	  setParameter(CanTalonSRX.param_t.eQuadIdxPolarity,risingEdge	? 1 : 0);
	  setParameter(CanTalonSRX.param_t.eClearPositionOnIdx,1);
	}else{
	  /* disable the feature first, then update the edge polarity. */
	  setParameter(CanTalonSRX.param_t.eClearPositionOnIdx,0);
	  setParameter(CanTalonSRX.param_t.eQuadIdxPolarity,risingEdge	? 1 : 0);
	}
  }
  @Override
  public void setExpiration(double timeout) {
    m_safetyHelper.setExpiration(timeout);
  }

  @Override
  public double getExpiration() {
    return m_safetyHelper.getExpiration();
  }

  @Override
  public boolean isAlive() {
    return m_safetyHelper.isAlive();
  }

  @Override
  public boolean isSafetyEnabled() {
    return m_safetyHelper.isSafetyEnabled();
  }

  @Override
  public void setSafetyEnabled(boolean enabled) {
    m_safetyHelper.setSafetyEnabled(enabled);
  }

  @Override
  public String getDescription() {
    return "CANTalon ID " + m_deviceNumber;
  }

  /*
   * Live Window code, only does anything if live window is activated.
   */

  private ITable m_table = null;
  private ITableListener m_table_listener = null;

  /**
   * {@inheritDoc}
   */
  @Override
  public void initTable(ITable subtable) {
    m_table = subtable;
    updateTable();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateTable() {
    CANSpeedController.super.updateTable();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ITable getTable() {
    return m_table;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void startLiveWindowMode() {
    set(0); // Stop for safety
    m_table_listener = createTableListener();
    m_table.addTableListener(m_table_listener, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stopLiveWindowMode() {
    set(0); // Stop for safety
    // TODO: See if this is still broken
    m_table.removeTableListener(m_table_listener);
  }

}
