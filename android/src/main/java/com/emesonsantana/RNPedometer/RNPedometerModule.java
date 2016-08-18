package com.emesonsantana.RNPedometer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Handler;
import android.os.Bundle;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

public class RNPedometerModule extends ReactContextBaseJavaModule implements SensorEventListener, StepListener {

  ReactApplicationContext reactContext;

  public static int STOPPED = 0;
  public static int STARTING = 1;
  public static int RUNNING = 2;
  public static int ERROR_FAILED_TO_START = 3;
  public static int ERROR_NO_SENSOR_FOUND = 4;
  public static float STEP_IN_METERS = 0.762f;

  private int status;     // status of listener
  private float numSteps; // number of the steps
  private float startNumSteps; //first value, to be substracted in step counter sensor type
  private long startAt; //time stamp of when the measurement starts

  private SensorManager sensorManager; // Sensor manager
  private Sensor mSensor;             // Pedometer sensor returned by sensor manager
  private StepDetector stepDetector;

  public RNPedometerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.reactContext.addLifecycleEventListener(this);

    this.startAt = 0;
    this.numSteps = 0;
    this.startNumSteps = 0;
    this.setStatus(RNPedometerModule.STOPPED);
    this.stepDetector = new StepDetector();
    this.stepDetector.registerListener(this);

    this.sensorManager = (SensorManager) this.reactContext.getSystemService(Context.SENSOR_SERVICE);
  }

  @Override
  public String getName() {
    return "RNPedometer";
  }

  @ReactMethod
  private void isStepCountingAvailable(Callback callback) {
    Sensor stepCounter = this.sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    Sensor accel = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (accel != null || stepCounter != null) {
      callback.invoke(true);
    } else {
      this.setStatus(RNPedometerModule.ERROR_NO_SENSOR_FOUND);
      callback.invoke(false);
    }
  }

  @ReactMethod
  private void isDistanceAvailable(Callback callback) {
    callback.invoke(false);
  }

  @ReactMethod
  private void isFloorCountingAvailable(Callback callback) {
    callback.invoke(false);
  }

  @ReactMethod
  private void isPaceAvailable(Callback callback) {
    callback.invoke(false);
  }

  @ReactMethod
  private void isCadenceAvailable(Callback callback) {
    callback.invoke(false);
  }

  @ReactMethod
  private void startPedometerUpdatesFromDate(Integer date) {
    if (this.status != PedometerListener.RUNNING) {
      // If not running, then this is an async call, so don't worry about waiting
      // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
      this.start();
    }
  }

  @ReactMethod
  private void stopPedometerUpdates() {
    if (this.status == RNPedometerModule.RUNNING) {
      this.stop();
    }
  }

  @ReactMethod
  private void queryPedometerDataBetweenDates(Integer startDate, Integer endDate, Callback callback) {
    callback.invoke(this.getStepsJSON());
  }

  @Override
  public void onHostDestroy() {
    this.stop();
  }

  /**
   * Called when the accuracy of the sensor has changed.
   */
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    //nothing to do here
    return;
  }

  /**
   * Sensor listener event.
   * @param event
   */
  @Override
  public void onSensorChanged(SensorEvent event) {
    // Only look at step counter or accelerometer events
    if (event.sensor.getType() != this.mSensor.getType()) {
      return;
    }

    // If not running, then just return
    if (this.status == RNPedometerModule.STOPPED) {
      return;
    }
    this.setStatus(RNPedometerModule.RUNNING);

    if(this.mSensor.getType() == Sensor.TYPE_STEP_COUNTER){
      float steps = event.values[0];

      if(this.startNumSteps == 0)
        this.startNumSteps = steps;

      this.numSteps = steps - this.startNumSteps;

      this.sendPedometerUpdateEvent(this.getStepsParamsMap());

    }else if(this.mSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      stepDetector.updateAccel(
          event.timestamp, event.values[0], event.values[1], event.values[2]);
        
    }
  }

  @Override
  public void step(long timeNs) {
    this.numSteps++;
    this.sendPedometerUpdateEvent(this.getStepsParamsMap());
  }

  /**
   * Start listening for pedometers sensor.
   */
  private void start() {
      // If already starting or running, then return
      if ((this.status == RNPedometerModule.RUNNING) || (this.status == RNPedometerModule.STARTING)) {
          return;
      }

      this.startAt = System.currentTimeMillis();
      this.numSteps = 0;
      this.startNumSteps = 0;
      this.setStatus(RNPedometerModule.STARTING);

      // Get pedometer or accelerometer from sensor manager
      this.mSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
      if(this.mSensor == null) this.mSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

      // If found, then register as listener
      if (this.mSensor != null) {
          int sensorDelay = this.mSensor.getType() == Sensor.TYPE_STEP_COUNTER ? SensorManager.SENSOR_DELAY_UI : SensorManager.SENSOR_DELAY_FASTEST;
          if (this.sensorManager.registerListener(this, this.mSensor, sensorDelay)) {
              this.setStatus(RNPedometerModule.STARTING);
          } else {
              this.setStatus(RNPedometerModule.ERROR_FAILED_TO_START);
              this.sendPedometerUpdateEvent(
                this.getErrorParamsMap(
                  RNPedometerModule.ERROR_FAILED_TO_START,
                  "Device sensor returned an error."
                )
              );
              return;
          };
      } else {
          this.setStatus(RNPedometerModule.ERROR_FAILED_TO_START);
          this.sendPedometerUpdateEvent(
            this.getErrorParamsMap(
              RNPedometerModule.ERROR_FAILED_TO_START,
              "No sensors found to register step counter listening to."
            )
          );
          return;
      }
  }

  /**
   * Stop listening to sensor.
   */
  private void stop() {
      if (this.status != RNPedometerModule.STOPPED) {
          this.sensorManager.unregisterListener(this);
      }
      this.setStatus(RNPedometerModule.STOPPED);
  }

  private void setStatus(int status) {
    this.status = status;
  }

  private WritableMap getStepsParamsMap() {
    WritableMap map = Arguments.createMap();
    // pedometerData.startDate; -> ms since 1970
    // pedometerData.endDate; -> ms since 1970
    // pedometerData.numberOfSteps;
    // pedometerData.distance;
    // pedometerData.floorsAscended;
    // pedometerData.floorsDescended;
    try {
        map.putInt("startDate", this.startAt);
        map.putInt("endDate", System.currentTimeMillis());
        map.putDouble("numberOfSteps", this.numSteps);
        map.putDouble("distance", this.numSteps * RNPedometerModule.STEP_IN_METERS);
    } catch (Exception e) {
        e.printStackTrace();
    }
    return map;
  }

  private WritableMap getErrorParamsMap(int code, String message) {
    // Error object
     WritableMap map = Arguments.createMap();
    try {
      map.putInt("code", code);
      map.putString("message", message);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return map;
  }

  private void sendPedometerUpdateEvent(@Nullable WritableMap params) {
    this.reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit("pedometerDataDidUpdate", params);
  }

}
