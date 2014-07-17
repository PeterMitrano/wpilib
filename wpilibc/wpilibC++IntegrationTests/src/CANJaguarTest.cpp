/*----------------------------------------------------------------------------*/
/* Copyright (c) FIRST 2014. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

#include "WPILib.h"
#include "gtest/gtest.h"
#include "TestBench.h"

static constexpr double kExpectedBusVoltage = 14.0;
static constexpr double kExpectedTemperature = 25.0;

static constexpr double kMotorTime = 0.5;

static constexpr double kEncoderSettlingTime = 1.0;
static constexpr double kEncoderPositionTolerance = 0.1;
static constexpr double kEncoderSpeedTolerance = 30.0;

static constexpr double kPotentiometerSettlingTime	 = 1.0;
static constexpr double kPotentiometerPositionTolerance = 0.1;

static constexpr double kCurrentTolerance = 0.1;

static constexpr double kVoltageTolerance = 0.1;

class CANJaguarTest : public testing::Test {
protected:
	CANJaguar *m_jaguar;
	DigitalOutput *m_fakeForwardLimit, *m_fakeReverseLimit;
	AnalogOutput *m_fakePotentiometer;

	virtual void SetUp() {
		m_jaguar = new CANJaguar(TestBench::kCANJaguarID);

		m_fakeForwardLimit = new DigitalOutput(TestBench::kFakeJaguarForwardLimit);
		m_fakeForwardLimit->Set(0);

		m_fakeReverseLimit = new DigitalOutput(TestBench::kFakeJaguarReverseLimit);
		m_fakeReverseLimit->Set(0);

		m_fakePotentiometer = new AnalogOutput(TestBench::kFakeJaguarPotentiometer);
		m_fakePotentiometer->SetVoltage(0.0f);

		/* The motor might still have momentum from the previous test. */
		Wait(kEncoderSettlingTime);
	}

	virtual void TearDown() {
		delete m_jaguar;
		delete m_fakeForwardLimit;
		delete m_fakeReverseLimit;
		delete m_fakePotentiometer;
	}

	/**
	 * Calls CANJaguar::Set periodically 50 times to make sure everything is
	 * verified.  This mimics a real robot program, where Set is presumably
	 * called in each iteration of the main loop.
	 */
	void SetJaguar(float totalTime, float value = 0.0f) {
		for(int i = 0; i < 50; i++) {
			m_jaguar->Set(value);
			Wait(totalTime / 50.0);
		}
	}
};

/**
 * Checks the default status data for reasonable values to confirm that we're
 * really getting status data from the Jaguar.
 */
TEST_F(CANJaguarTest, InitialStatus) {
	m_jaguar->SetPercentMode();

	EXPECT_NEAR(m_jaguar->GetBusVoltage(), kExpectedBusVoltage, 3.0)
		<< "Bus voltage is not a plausible value.";

	EXPECT_FLOAT_EQ(m_jaguar->GetOutputVoltage(), 0.0)
		<< "Output voltage is non-zero.";

	EXPECT_FLOAT_EQ(m_jaguar->GetOutputCurrent(), 0.0)
		<< "Output current is non-zero.";

	EXPECT_NEAR(m_jaguar->GetTemperature(), kExpectedTemperature, 5.0)
		<< "Temperature is not a plausible value.";

	EXPECT_EQ(m_jaguar->GetFaults(), 0)
		<< "Jaguar has one or more fault set.";
}

/**
 * Test if we can set arbitrary setpoints and PID values each each applicable
 * mode and get the same values back.
 */
TEST_F(CANJaguarTest, SetGet) {
	m_jaguar->DisableControl();

	m_jaguar->SetSpeedMode(CANJaguar::QuadEncoder, 360, 1, 2, 3);
	m_jaguar->Set(4);

	EXPECT_FLOAT_EQ(1, m_jaguar->GetP());
	EXPECT_FLOAT_EQ(2, m_jaguar->GetI());
	EXPECT_FLOAT_EQ(3, m_jaguar->GetD());
	EXPECT_FLOAT_EQ(4, m_jaguar->Get());
}

/**
 * Test if we can drive the motor in percentage mode and get a position back
 */
TEST_F(CANJaguarTest, PercentForwards) {
	m_jaguar->SetPercentMode(CANJaguar::QuadEncoder, 360);
	m_jaguar->EnableControl();

	/* The motor might still have momentum from the previous test. */
	SetJaguar(kEncoderSettlingTime, 0.0f);

	double initialPosition = m_jaguar->GetPosition();

	/* Drive the speed controller briefly to move the encoder */
	SetJaguar(kMotorTime, 1.0f);
	m_jaguar->Set(0.0f);

	/* The position should have increased */
	EXPECT_GT(m_jaguar->GetPosition(), initialPosition)
		<< "CAN Jaguar position should have increased after the motor moved";
}

/**
 * Test if we can drive the motor backwards in percentage mode and get a
 * position back
 */
TEST_F(CANJaguarTest, PercentReverse) {
	m_jaguar->SetPercentMode(CANJaguar::QuadEncoder, 360);
	m_jaguar->EnableControl();

	/* The motor might still have momentum from the previous test. */
	SetJaguar(kEncoderSettlingTime, 0.0f);

	double initialPosition = m_jaguar->GetPosition();

	/* Drive the speed controller briefly to move the encoder */
	SetJaguar(kMotorTime, -1.0f);
	m_jaguar->Set(0.0f);

	float p = m_jaguar->GetPosition();
	/* The position should have decreased */
	EXPECT_LT(p, initialPosition)
		<< "CAN Jaguar position should have decreased after the motor moved";
}

/**
 * Test if we can set an absolute voltage and receive a matching output voltage
 * status.
 */
TEST_F(CANJaguarTest, Voltage) {
	m_jaguar->SetVoltageMode();
	m_jaguar->EnableControl();

	SetJaguar(kMotorTime, M_PI);
	m_jaguar->Set(0.0f);
	EXPECT_NEAR(M_PI, m_jaguar->GetOutputVoltage(), kVoltageTolerance);

	SetJaguar(kMotorTime, 8.0f);
	m_jaguar->Set(0.0f);
	EXPECT_NEAR(8.0f, m_jaguar->GetOutputVoltage(), kVoltageTolerance);
}

/**
 * Test if we can set a speed in speed control mode and receive a matching
 * speed status.
 */
TEST_F(CANJaguarTest, SpeedPID) {
	m_jaguar->SetSpeedMode(CANJaguar::QuadEncoder, 360, 0.1f, 0.003f, 0.01f);
	m_jaguar->EnableControl();

	constexpr float speed = 200.0f;

	SetJaguar(kMotorTime, speed);
	EXPECT_NEAR(speed, m_jaguar->GetSpeed(), kEncoderSpeedTolerance);
}

/**
 * Test if we can set a position and reach that position with PID control on
 * the Jaguar.
 */
TEST_F(CANJaguarTest, EncoderPositionPID) {
	m_jaguar->SetPositionMode(CANJaguar::QuadEncoder, 360, 10.0f, 0.1f, 0.0f);

	double setpoint = m_jaguar->GetPosition() + 10.0f;

	m_jaguar->EnableControl();

	/* It should get to the setpoint within 10 seconds */
	for(int i = 0; i < 10; i++) {
		SetJaguar(1.0f, setpoint);

		if(std::abs(m_jaguar->GetPosition() - setpoint) <= kEncoderPositionTolerance) {
			return;
		}
	}

	EXPECT_NEAR(setpoint, m_jaguar->GetPosition(), kEncoderPositionTolerance)
		<< "CAN Jaguar should have reached setpoint with PID control";
}

/**
 * Test if we can set a current setpoint with PID control on the Jaguar and get
 * a corresponding output current
 */
TEST_F(CANJaguarTest, CurrentPID) {
	m_jaguar->SetCurrentMode(10.0, 4.0, 1.0);
	m_jaguar->EnableControl();

	float setpoint =  1.6f;

	/* It should get to the setpoint within 10 seconds */
	for(int i = 0; i < 10; i++) {
		SetJaguar(1.0, setpoint);

		if(std::abs(m_jaguar->GetOutputCurrent() - setpoint) <= kCurrentTolerance) {
			break;
		}
	}

	EXPECT_NEAR(setpoint, m_jaguar->GetOutputCurrent(), kCurrentTolerance);

	setpoint =  2.0f;

	/* It should get to the setpoint within 10 seconds */
	for(int i = 0; i < 10; i++) {
		SetJaguar(1.0, setpoint);

		if(std::abs(m_jaguar->GetOutputCurrent() - setpoint) <= kCurrentTolerance) {
			break;
		}
	}

	EXPECT_NEAR(setpoint, m_jaguar->GetOutputCurrent(), kCurrentTolerance);
}

/**
 * Test if we can get a position in potentiometer mode, using an analog output
 * as a fake potentiometer.
 */
TEST_F(CANJaguarTest, FakePotentiometerPosition) {
	m_jaguar->SetPercentMode(CANJaguar::Potentiometer);
	m_jaguar->EnableControl();

	// Set the analog output to 4 different voltages and check if the Jaguar
	// returns corresponding positions.
	for(int i = 0; i <= 3; i++) {
		m_fakePotentiometer->SetVoltage(static_cast<float>(i));

		SetJaguar(kPotentiometerSettlingTime);

		EXPECT_NEAR(m_fakePotentiometer->GetVoltage() / 3.0f, m_jaguar->GetPosition(), kPotentiometerPositionTolerance)
			<< "CAN Jaguar should have returned the potentiometer position set by the analog output";
	}
}

/**
 * Test if we can limit the Jaguar to only moving in reverse with a fake
 * limit switch.
 */
TEST_F(CANJaguarTest, FakeLimitSwitchForwards) {
	m_jaguar->SetPercentMode(CANJaguar::QuadEncoder, 360);
	m_jaguar->ConfigLimitMode(CANJaguar::kLimitMode_SwitchInputsOnly);
	m_fakeForwardLimit->Set(1);
	m_fakeReverseLimit->Set(0);
	m_jaguar->EnableControl();

	SetJaguar(kEncoderSettlingTime);

	/* Make sure the limits are recognized by the Jaguar. */
	ASSERT_FALSE(m_jaguar->GetForwardLimitOK());
	ASSERT_TRUE(m_jaguar->GetReverseLimitOK());

	double initialPosition = m_jaguar->GetPosition();

	/* Drive the speed controller briefly to move the encoder.  If the limit
		 switch is recognized, it shouldn't actually move. */
	SetJaguar(kMotorTime, 1.0f);

	/* The position should be the same, since the limit switch was on. */
	EXPECT_NEAR(initialPosition, m_jaguar->GetPosition(), kEncoderPositionTolerance)
		<< "CAN Jaguar should not have moved with the limit switch pressed";

	/* Drive the speed controller in the other direction.  It should actually
		 move, since only the forward switch is activated.*/
	SetJaguar(kMotorTime, -1.0f);

	/* The position should have decreased */
	EXPECT_LT(m_jaguar->GetPosition(), initialPosition)
		<< "CAN Jaguar should have moved in reverse while the forward limit was on";
}

/**
 * Test if we can limit the Jaguar to only moving forwards with a fake limit
 * switch.
 */
TEST_F(CANJaguarTest, FakeLimitSwitchReverse) {
	m_jaguar->SetPercentMode(CANJaguar::QuadEncoder, 360);
	m_jaguar->ConfigLimitMode(CANJaguar::kLimitMode_SwitchInputsOnly);
	m_fakeForwardLimit->Set(0);
	m_fakeReverseLimit->Set(1);
	m_jaguar->EnableControl();

	SetJaguar(kEncoderSettlingTime);

	/* Make sure the limits are recognized by the Jaguar. */
	ASSERT_TRUE(m_jaguar->GetForwardLimitOK());
	ASSERT_FALSE(m_jaguar->GetReverseLimitOK());

	double initialPosition = m_jaguar->GetPosition();

	/* Drive the speed controller backwards briefly to move the encoder.  If
		 the limit switch is recognized, it shouldn't actually move. */
	SetJaguar(kMotorTime, -1.0f);

	/* The position should be the same, since the limit switch was on. */
	EXPECT_NEAR(initialPosition, m_jaguar->GetPosition(), kEncoderPositionTolerance)
		<< "CAN Jaguar should not have moved with the limit switch pressed";

	/* Drive the speed controller in the other direction.  It should actually
		 move, since only the reverse switch is activated.*/
	SetJaguar(kMotorTime, 1.0f);

	/* The position should have increased */
	EXPECT_GT(m_jaguar->GetPosition(), initialPosition)
		<< "CAN Jaguar should have moved forwards while the reverse limit was on";
}