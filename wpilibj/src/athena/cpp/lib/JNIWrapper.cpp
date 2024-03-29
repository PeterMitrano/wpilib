#include <jni.h>
#include <assert.h>
#include "Log.hpp"

#include "edu_wpi_first_wpilibj_hal_JNIWrapper.h"

#include "HAL/HAL.hpp"

extern "C" {

/*
 * Class:     edu_wpi_first_wpilibj_hal_JNIWrapper
 * Method:    getPortWithModule
 * Signature: (BB)J
 */
JNIEXPORT jlong JNICALL Java_edu_wpi_first_wpilibj_hal_JNIWrapper_getPortWithModule
  (JNIEnv * env, jclass, jbyte module, jbyte pin)
{
	//FILE_LOG(logDEBUG) << "Calling JNIWrapper getPortWithModlue";
	//FILE_LOG(logDEBUG) << "Module = " << (jint)module;
	//FILE_LOG(logDEBUG) << "Pin = " << (jint)pin;
	void* port = getPortWithModule(module, pin);
	//FILE_LOG(logDEBUG) << "Port Ptr = " << port;
	return (jlong)port;
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_JNIWrapper
 * Method:    getPort
 * Signature: (BB)J
 */
JNIEXPORT jlong JNICALL Java_edu_wpi_first_wpilibj_hal_JNIWrapper_getPort
  (JNIEnv * env, jclass, jbyte pin)
{
	//FILE_LOG(logDEBUG) << "Calling JNIWrapper getPortWithModlue";
	//FILE_LOG(logDEBUG) << "Module = " << (jint)module;
	//FILE_LOG(logDEBUG) << "Pin = " << (jint)pin;
	void* port = getPort(pin);
	//FILE_LOG(logDEBUG) << "Port Ptr = " << port;
	return (jlong)port;
}

}  // extern "C"
