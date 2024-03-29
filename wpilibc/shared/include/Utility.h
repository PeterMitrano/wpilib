/*---------------------------------------------------------------------------*/
/* Copyright (c) FIRST 2008. All Rights Reserved.
 */
/* Open Source Software - may be modified and shared by FRC teams. The code  */
/* must be accompanied by the FIRST BSD license file in $(WIND_BASE)/WPILib. */
/*---------------------------------------------------------------------------*/
#pragma once

/** @file
 * Contains global utility functions
 */

#include <stdint.h>
#include <string>

#define wpi_assert(condition) \
  wpi_assert_impl(condition, #condition, "", __FILE__, __LINE__, __FUNCTION__)
#define wpi_assertWithMessage(condition, message)                     \
  wpi_assert_impl(condition, #condition, message, __FILE__, __LINE__, \
                  __FUNCTION__)

#define wpi_assertEqual(a, b) \
  wpi_assertEqual_impl(a, b, #a, #b, "", __FILE__, __LINE__, __FUNCTION__)
#define wpi_assertEqualWithMessage(a, b, message) \
  wpi_assertEqual_impl(a, b, #a, #b, message, __FILE__, __LINE__, __FUNCTION__)

#define wpi_assertNotEqual(a, b) \
  wpi_assertNotEqual_impl(a, b, #a, #b, "", __FILE__, __LINE__, __FUNCTION__)
#define wpi_assertNotEqualWithMessage(a, b, message)                 \
  wpi_assertNotEqual_impl(a, b, #a, #b, message, __FILE__, __LINE__, \
                          __FUNCTION__)

bool wpi_assert_impl(bool conditionValue, const std::string &conditionText,
                     const std::string &message, const std::string &fileName,
                     uint32_t lineNumber, const std::string &funcName);
bool wpi_assertEqual_impl(int valueA, int valueB, const std::string &valueAString,
                          const std::string &valueBString, const std::string &message,
                          const std::string &fileName, uint32_t lineNumber,
                          const std::string &funcName);
bool wpi_assertNotEqual_impl(int valueA, int valueB, const std::string &valueAString,
                             const std::string &valueBString, const std::string &message,
                             const std::string &fileName, uint32_t lineNumber,
                             const std::string &funcName);

void wpi_suspendOnAssertEnabled(bool enabled);

uint16_t GetFPGAVersion();
uint32_t GetFPGARevision();
uint32_t GetFPGATime();
bool GetUserButton();
std::string GetStackTrace(uint32_t offset);
