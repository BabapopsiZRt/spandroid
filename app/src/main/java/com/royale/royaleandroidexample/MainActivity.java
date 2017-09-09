/****************************************************************************\
 * Copyright (C) 2016 Infineon Technologies
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 \****************************************************************************/

package com.royale.royaleandroidexample;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.widget.TextView;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.github.paolorotolo.appintro.AppIntro2;
import com.github.paolorotolo.appintro.AppIntroFragment;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

public class MainActivity extends AppIntro2 implements TextToSpeech.OnInitListener {
  private static final String LOG_TAG = "SPAN";
  private static final String ACTION_USB_SENSOR_PERMISSION = "ACTION_USB_SENSOR_PERMISSION";
  private static final String ACTION_USB_ARDUINO_PERMISSION = "ACTION_USB_ARDUINO_PERMISSION";
  static int fd;
  static ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 50);

  static {
    System.loadLibrary("usb_android");
    System.loadLibrary("uvc");
    System.loadLibrary("royale");
    System.loadLibrary("royaleSample");
  }

  boolean m_opened;
  int scaleFactor;
  int[] resolution;
  TextToSpeech tts;
  private PendingIntent sensorPendingIntent;
  private UsbManager usbManager;
  private UsbDeviceConnection usbConnection;
  private Bitmap bmp = null;
  private UsbDeviceConnection connection;
  private UsbSerialDevice arduino;
  private PendingIntent arduinoPendingIntent;
  private TextView txt;
  private int howCloseInCm = 1100;
  private Handler beepHandler;
  private Runnable beepRunnable = new Runnable() {
    @Override public void run() {
      if (howCloseInCm != 1100) {
        if (howCloseInCm < 700) {
          beep(Math.min(100, howCloseInCm * 10));
        }
      }
      beepHandler.postDelayed(this, Math.min(1000, howCloseInCm * 10));
    }
  };
  //broadcast receiver for user usb permission dialog
  private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action == null) {
        return;
      }
      switch (action) {
        case ACTION_USB_SENSOR_PERMISSION:
          UsbDevice sensor = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            if (sensor != null) {
              RegisterCallback();
              permissionCallbackSensor(sensor);
              createBitmap();
            }
          } else {
            System.out.println("permission denied for device" + sensor);
          }
          break;

        case ACTION_USB_ARDUINO_PERMISSION:
          UsbDevice actuator = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
          openArduino(actuator, granted);
          break;

        case UsbManager.ACTION_USB_DEVICE_ATTACHED:
          openDevices();
          break;
      }
    }
  };

  private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
    //Defining a Callback which triggers whenever data is read.
    @Override
    public void onReceivedData(byte[] arg0) {
      String data = null;
      try {
        data = new String(arg0, "UTF-8");
        Log.w(LOG_TAG, "onReceivedData: " + data);
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }
    }
  };

  private void openArduino(UsbDevice actuator, boolean granted) {
    if (granted) {
      connection = usbManager.openDevice(actuator);
      arduino = UsbSerialDevice.createUsbSerialDevice(actuator, connection);
      if (arduino != null) {
        if (arduino.open()) {
          arduino.setBaudRate(115200);
          arduino.setDataBits(UsbSerialInterface.DATA_BITS_8);
          arduino.setStopBits(UsbSerialInterface.STOP_BITS_1);
          arduino.setParity(UsbSerialInterface.PARITY_NONE);
          arduino.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
          arduino.read(mCallback);
        } else {
          Log.d("SERIAL", "PORT NOT OPEN");
        }
      } else {
        Log.d("SERIAL", "PORT IS NULL");
      }
    } else {
      Log.d("SERIAL", "PERMISSION NOT GRANTED");
    }
  }

  public static void beep(int duration) {
    toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, duration);
  }

  public native int[] OpenCameraNative(int fd, int vid, int pid);

  public native void setCameraParams(int tresholdCm, int minStrength);

  public native void CloseCameraNative();

  public native void RegisterCallback();

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addSlide(AppIntroFragment.newInstance("Connect the Sensor",
        "Please attatch the Pico to the USB hub on your phone",
        R.drawable.ic_skip_white,
        Color.BLACK));
    addSlide(AppIntroFragment.newInstance("Connect the Feedback",
        "Please attatch the Feedback Arduino to the USB hub",
        R.drawable.ic_arrow_forward_white,
        Color.BLACK));
    addSlide(DepthCameraFragment.newInstance());

    showSkipButton(false);
    // setProgressButtonEnabled(false);

    setVibrate(true);
    setVibrateIntensity(30);

    tts = new TextToSpeech(this, this);
    tts.setLanguage(Locale.US);

    Log.d(LOG_TAG, "onCreate()");
  }

  private void speak(String text) {
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
  }

  private String writeToArduino(int[] values) {
    StringBuilder data = new StringBuilder();
    for (int value : values) {
      data.append(value).append(";");
    }
    data.replace(data.length()-1, data.length(), "\n");

    if (arduino == null) {
      return data.toString();
    }

    arduino.write(data.toString().getBytes());

    Log.w(LOG_TAG, "writeToArduino: " + data.toString());

    return data.toString();
  }

  private static int FPS = 1;

  private static final int CAPTURE_FPS = 5;

  private int currentFrame = 0;
  private int[] sumForFireFrame = {0,0,0,0,0,0};

  private void resetSumFireFrame() {
    for (int i = 0; i < sumForFireFrame.length; i++) {
      sumForFireFrame[i] = 0;
    }
  }

  private void sendDataOrAverage(int[] segment6) {

  }

  @Override protected void onPause() {
    if (m_opened) {
      CloseCameraNative();
      m_opened = false;
    }
    super.onPause();
    Log.d(LOG_TAG, "onPause()");
    unregisterReceiver(mUsbReceiver);
  }

  @Override protected void onResume() {
    super.onResume();
    Log.d(LOG_TAG, "onResume()");
    registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_SENSOR_PERMISSION));
    registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_ARDUINO_PERMISSION));
  }

  @Override protected void onDestroy() {
    Log.d(LOG_TAG, "onDestroy()");
    unregisterReceiver(mUsbReceiver);

    if (usbConnection != null) {
      usbConnection.close();
    }

    super.onDestroy();
  }

  @Override public void onSlideChanged(
      @Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
    super.onSlideChanged(oldFragment, newFragment);
    openDevices();
  }

  public void amplitudeCallback(
      final int[] amplitudes,
      final float[] rawDepth,
      final int[] segmentDist6,
      final int[] segmentStrength6,
      final float min,
      final float max,
      final int badCount,
      final int retry) {
    if (!m_opened) {
      Log.d(LOG_TAG, "Device in Java not initialized");
      return;
    }
    int minDist = 1100;
    for (int cm : segmentDist6) {
      minDist = cm < minDist ? cm : minDist;
    }
    howCloseInCm = minDist;

    final String writtenToArduino = writeToArduino(segmentDist6);

    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (DepthCameraFragment.amplitudeView != null) {
          bmp.setPixels(amplitudes, 0, resolution[0], 0, 0, resolution[0], resolution[1]);
          DepthCameraFragment.amplitudeView.setImageBitmap(Bitmap.createScaledBitmap(bmp,
              resolution[0] * scaleFactor,
              resolution[1] * scaleFactor,
              false));
        }
        if (DepthCameraFragment.debugTxt != null) {
          DepthCameraFragment.debugTxt.setText(writtenToArduino);
        }
      }
    });
  }

  public void openDevices() {
    Log.d(LOG_TAG, "openDevices");

    //check permission and request if not granted yet
    usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

    if (usbManager != null) {
      Log.d(LOG_TAG, "Manager valid");
    } else {
      Log.e(LOG_TAG, "Manager not valid");
      return;
    }

    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

    Log.d(LOG_TAG, "USB Devices : " + deviceList.size());

    Iterator<UsbDevice> iterator = deviceList.values().iterator();
    UsbDevice device;
    while (iterator.hasNext()) {
      device = iterator.next();
      Log.i(LOG_TAG, "openDevices DEVICE: " + device.getVendorId());
      if (device.getVendorId() == 0x1C28
          || device.getVendorId() == 0x058B
          || device.getVendorId() == 0x1f46) {
        Log.d(LOG_TAG, "royale device found");
        if (!usbManager.hasPermission(device)) {
          Intent intent = new Intent(ACTION_USB_SENSOR_PERMISSION);
          intent.setAction(ACTION_USB_SENSOR_PERMISSION);
          sensorPendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
          usbManager.requestPermission(device, sensorPendingIntent);
        } else {
          RegisterCallback();
          permissionCallbackSensor(device);
          createBitmap();
        }
      } else if (device.getVendorId() == 1027 || device.getVendorId() == 9025) { //Arduino Vendor ID
        if (!usbManager.hasPermission(device)) {
          Log.d(LOG_TAG, "arduino device found");
          Intent intent = new Intent(ACTION_USB_ARDUINO_PERMISSION);
          intent.setAction(ACTION_USB_ARDUINO_PERMISSION);
          arduinoPendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
          usbManager.requestPermission(device, arduinoPendingIntent);
        } else {
          openArduino(device, true);
        }
      }
    }
  }

  private void permissionCallbackSensor(UsbDevice device) {
    usbConnection = usbManager.openDevice(device);
    Log.i(LOG_TAG,
        "permission granted for: "
            + device.getDeviceName()
            + ", fileDesc: "
            + usbConnection.getFileDescriptor());

    int fd = usbConnection.getFileDescriptor();

    resolution = OpenCameraNative(fd, device.getVendorId(), device.getProductId());

    setCameraParams(10, 120);

    beepHandler = new Handler(getMainLooper());
    beepHandler.post(beepRunnable);

    if (resolution[0] > 0) {
      m_opened = true;
    }
  }

  private void createBitmap() {

    // calculate scale factor, which scales the bitmap relative to the disyplay resolution
    Display display = getWindowManager().getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    double displayWidth = size.x * 0.9;
    scaleFactor = (int) displayWidth / resolution[0];

    if (bmp == null) {
      bmp = Bitmap.createBitmap(resolution[0], resolution[1], Bitmap.Config.ARGB_8888);
    }
  }

  @Override public void onInit(int status) {

  }
}
