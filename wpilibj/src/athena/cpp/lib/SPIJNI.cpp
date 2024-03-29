#include <jni.h>
#include <assert.h>
#include "Log.hpp"

#include "edu_wpi_first_wpilibj_hal_SPIJNI.h"

#include "HAL/Digital.hpp"
#include "HALUtil.h"

// set the logging level
TLogLevel spiJNILogLevel = logWARNING;

#define SPIJNI_LOG(level) \
    if (level > spiJNILogLevel) ; \
    else Log().Get(level)

extern "C" {

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiInitialize
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiInitialize
  (JNIEnv * env, jclass, jbyte port)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiInitialize";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	int32_t status = 0;
	spiInitialize(port, &status);
	SPIJNI_LOG(logDEBUG) << "Status = " << status;
	CheckStatus(env, status);
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiTransaction
 * Signature: (BLjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;B)I
 */
JNIEXPORT jint JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiTransaction
  (JNIEnv * env, jclass, jbyte port, jobject dataToSend, jobject dataReceived, jbyte size)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiTransaction";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	uint8_t* dataToSendPtr = nullptr;
	if (dataToSend != 0) {
		dataToSendPtr = (uint8_t*)env->GetDirectBufferAddress(dataToSend);
	}
	uint8_t* dataReceivedPtr = (uint8_t*)env->GetDirectBufferAddress(dataReceived);
	SPIJNI_LOG(logDEBUG) << "Size = " << (jint)size;
	SPIJNI_LOG(logDEBUG) << "DataToSendPtr = " << dataToSendPtr;
	SPIJNI_LOG(logDEBUG) << "DataReceivedPtr = " << dataReceivedPtr;
	jint retVal = spiTransaction(port, dataToSendPtr, dataReceivedPtr, size);
	SPIJNI_LOG(logDEBUG) << "ReturnValue = " << (jint)retVal;
	return retVal;
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiWrite
 * Signature: (BLjava/nio/ByteBuffer;B)I
 */
JNIEXPORT jint JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiWrite
  (JNIEnv * env, jclass, jbyte port, jobject dataToSend, jbyte size)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiWrite";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	uint8_t* dataToSendPtr = nullptr;
	if (dataToSend != 0) {
		dataToSendPtr = (uint8_t*)env->GetDirectBufferAddress(dataToSend);
	}
	SPIJNI_LOG(logDEBUG) << "Size = " << (jint)size;
	SPIJNI_LOG(logDEBUG) << "DataToSendPtr = " << dataToSendPtr;
	jint retVal = spiWrite(port, dataToSendPtr, size);
	SPIJNI_LOG(logDEBUG) << "ReturnValue = " << (jint)retVal;
	return retVal;
}


/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiRead
 * Signature: (BLjava/nio/ByteBuffer;B)I
 */
JNIEXPORT jint JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiRead
  (JNIEnv * env, jclass, jbyte port, jobject dataReceived, jbyte size)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiRead";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	uint8_t* dataReceivedPtr = (uint8_t*)env->GetDirectBufferAddress(dataReceived);
	SPIJNI_LOG(logDEBUG) << "Size = " << (jint)size;
	SPIJNI_LOG(logDEBUG) << "DataReceivedPtr = " << dataReceivedPtr;
	jint retVal = spiRead(port, (uint8_t*)dataReceivedPtr, size);
	SPIJNI_LOG(logDEBUG) << "ReturnValue = " << (jint)retVal;
	return retVal;
}


/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiClose
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiClose
  (JNIEnv *, jclass, jbyte port)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiClose";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	spiClose(port);
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiSetSpeed
 * Signature: (BI)V
 */
JNIEXPORT void JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiSetSpeed
  (JNIEnv *, jclass, jbyte port, jint speed)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiSetSpeed";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	SPIJNI_LOG(logDEBUG) << "Speed = " << (jint) speed;
	spiSetSpeed(port, speed);
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiSetOpts
 * Signature: (BIII)V
 */
JNIEXPORT void JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiSetOpts
  (JNIEnv *, jclass, jbyte port, jint msb_first, jint sample_on_trailing, jint clk_idle_high)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiSetOpts";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	SPIJNI_LOG(logDEBUG) << "msb_first = " << msb_first;
	SPIJNI_LOG(logDEBUG) << "sample_on_trailing = " << sample_on_trailing;
	SPIJNI_LOG(logDEBUG) << "clk_idle_high = " << clk_idle_high;
	spiSetOpts(port, msb_first, sample_on_trailing, clk_idle_high);
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiSetChipSelectActiveHigh
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiSetChipSelectActiveHigh
  (JNIEnv * env, jclass, jbyte port)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiSetCSActiveHigh";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	int32_t status = 0;
	spiSetChipSelectActiveHigh(port, &status);
	SPIJNI_LOG(logDEBUG) << "Status = " << status;
	CheckStatus(env, status);
}

/*
 * Class:     edu_wpi_first_wpilibj_hal_SPIJNI
 * Method:    spiSetChipSelectActiveLow
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_edu_wpi_first_wpilibj_hal_SPIJNI_spiSetChipSelectActiveLow
  (JNIEnv * env, jclass, jbyte port)
{
	SPIJNI_LOG(logDEBUG) << "Calling SPIJNI spiSetCSActiveLow";
	SPIJNI_LOG(logDEBUG) << "Port = " << (jint) port;
	int32_t status = 0;
	spiSetChipSelectActiveLow(port, &status);
	SPIJNI_LOG(logDEBUG) << "Status = " << status;
	CheckStatus(env, status);
}

}  // extern "C"
