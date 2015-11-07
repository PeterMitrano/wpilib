/*----------------------------------------------------------------------------*/
/* Copyright (c) FIRST 2008. All Rights Reserved.
 */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in $(WIND_BASE)/WPILib.  */
/*----------------------------------------------------------------------------*/

#include "RobotBase.h"

#include "DriverStation.h"
//#include "NetworkCommunication/FRCComm.h"
//#include "NetworkCommunication/symModuleLink.h"
//#include "NetworkCommunication/UsageReporting.h"
#include "RobotState.h"
#include "HLUsageReporting.h"
#include "Internal/HardwareHLReporting.h"
#include "Utility.h"
#include "networktables/NetworkTable.h"
#include <cstring>
#include "HAL/HAL.hpp"
#include <cstdio>

RobotBase *RobotBase::m_instance = nullptr;

void RobotBase::setInstance(RobotBase *robot) {
  wpi_assert(m_instance == nullptr);
  m_instance = robot;
}

RobotBase &RobotBase::getInstance() { return *m_instance; }

void RobotBase::robotSetup(RobotBase *robot) {
  robot->Prestart();
  robot->StartCompetition();
}

/**
 * Constructor for a generic robot program.
 * User code should be placed in the constructor that runs before the Autonomous
 * or Operator
 * Control period starts. The constructor will run to completion before
 * Autonomous is entered.
 *
 * This must be used to ensure that the communications code starts. In the
 * future it would be
 * nice to put this code into it's own task that loads on boot so ensure that it
 * runs.
 */
RobotBase::RobotBase() : m_ds(DriverStation::GetInstance()) {
  RobotState::SetImplementation(DriverStation::GetInstance());
  HLUsageReporting::SetImplementation(new HardwareHLReporting());

  RobotBase::setInstance(this);

  NetworkTable::SetNetworkIdentity("Robot");

  FILE *file = nullptr;
  file = fopen("/tmp/frc_versions/FRC_Lib_Version.ini", "w");

  fputs("2016 C++ Beta2.0", file);
  if (file != nullptr) fclose(file);
}

/**
 * Free the resources for a RobotBase class.
 * This includes deleting all classes that might have been allocated as
 * Singletons to they
 * would never be deleted except here.
 */
RobotBase::~RobotBase() {
  SensorBase::DeleteSingletons();
  delete m_task;
  m_task = nullptr;
  m_instance = nullptr;
}

/**
 * Determine if the Robot is currently enabled.
 * @return True if the Robot is currently enabled by the field controls.
 */
bool RobotBase::IsEnabled() const { return m_ds.IsEnabled(); }

/**
 * Determine if the Robot is currently disabled.
 * @return True if the Robot is currently disabled by the field controls.
 */
bool RobotBase::IsDisabled() const { return m_ds.IsDisabled(); }

/**
 * Determine if the robot is currently in Autonomous mode.
 * @return True if the robot is currently operating Autonomously as determined
 * by the field controls.
 */
bool RobotBase::IsAutonomous() const { return m_ds.IsAutonomous(); }

/**
 * Determine if the robot is currently in Operator Control mode.
 * @return True if the robot is currently operating in Tele-Op mode as
 * determined by the field controls.
 */
bool RobotBase::IsOperatorControl() const { return m_ds.IsOperatorControl(); }

/**
 * Determine if the robot is currently in Test mode.
 * @return True if the robot is currently running tests as determined by the
 * field controls.
 */
bool RobotBase::IsTest() const { return m_ds.IsTest(); }

/**
 * This hook is called right before startCompetition(). By default, tell the DS
 * that the robot is now ready to
 * be enabled. If you don't want for the robot to be enabled yet, you can
 * override this method to do nothing.
 * If you do so, you will need to call
 * HALNetworkCommunicationObserveUserProgramStarting() from your code when
 * you are ready for the robot to be enabled.
 */
void RobotBase::Prestart() {
  HALNetworkCommunicationObserveUserProgramStarting();
}

/**
 * Indicates if new data is available from the driver station.
 * @return Has new data arrived over the network since the last time this
 * function was called?
 */
bool RobotBase::IsNewDataAvailable() const { return m_ds.IsNewControlData(); }

/**
 * This class exists for the sole purpose of getting its destructor called when
 * the module unloads.
 * Before the module is done unloading, we need to delete the RobotBase derived
 * singleton.  This should delete
 * the other remaining singletons that were registered.  This should also stop
 * all tasks that are using
 * the Task class.
 */
class RobotDeleter {
 public:
  RobotDeleter() {}
  ~RobotDeleter() { delete &RobotBase::getInstance(); }
};
static RobotDeleter g_robotDeleter;
