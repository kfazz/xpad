package com.kfazz.xpad;
import java.util.ArrayList;

import com.kfazz.xpad.R;
import com.kfazz.xpad.GameView;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

//Main Activity analogous to driver class in java prjoect
public class MainActivity extends Activity {

	private static final String TAG = "XpadMainActivity";

	private UsbManager mManager;
	private ArrayList<XpadDevice> mXpadDevices = new ArrayList<XpadDevice>(); //Handle more than one controler

	private GameView mGame;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate()");

		//show GameView
		setContentView(R.layout.game_view);
		mGame = (GameView) findViewById(R.id.game);

		// get handle to system usb manager
		mManager = (UsbManager)getSystemService(Context.USB_SERVICE);

		// check for existing devices
		for (UsbDevice device :  mManager.getDeviceList().values()) {
			Log.d(TAG, "onCreate() probing device: " + device);
			probeXpad(device);
		}

		// listen for new devices
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);

	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		mGame.requestFocus();
	}

	@Override
	public void onResume() {
		super.onResume();

		Intent intent = getIntent();
		Log.d(TAG, "intent: " + intent);
		String action = intent.getAction();

		UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
			probeXpad(device);
		} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
			for(XpadDevice d : mXpadDevices)
				if (d.mDevice.equals(device)) {
					d.XpadStop();
				}
		}	     
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy()");
		unregisterReceiver(mUsbReceiver);
		for( XpadDevice pad : mXpadDevices)
			pad.XpadStop();//kill all pads
		super.onDestroy();
	}

	// Sets the current USB device and interface
	private boolean addXpad(UsbDevice device, UsbInterface intf, boolean isWireless) {

		if (!mManager.hasPermission(device))
		{
			Log.d(TAG, "Permission Denied for device:" + device);
			return false;
		}

		UsbDeviceConnection connection = mManager.openDevice(device);
		if (connection != null) {
			if (connection.claimInterface(intf, true)) //true here kicks out the kernel driver
			{
				mXpadDevices.add( new XpadDevice(this, device, connection, intf, isWireless, mXpadDevices.size()+1));
				mXpadDevices.get(mXpadDevices.size()-1).XpadStart(); //setup controller instance
				return true;
			} else {
				connection.close();
				Log.d(TAG, "Failed to claim interface" + intf.getId() + " on device" + device);
				return false;
			}
		} else {
			Log.d(TAG, "Open failed for device: " + device.getDeviceName());
			return false;
		}

	}

	private boolean probeXpad(UsbDevice device) {
		Log.d(TAG, "in probeXpad. Device: " + device);
		for (XpadDevice d : mXpadDevices)
		{
			if (d.mDevice.equals(device)) {
				Log.d(TAG,"Skipping device, we already own it.");
				return false;
			}
		}
		//switch based on IDs in device_filter.xml
		//wireless dongles support 4 interfaces, one per controller

		/*Wired Controller:
		 *	Interface 0:
  			Endpoint 1(in):  Controller events
  			Endpoint 2(out): Messages to the controller 
  		  Wireless:
  			Interface 0:
  			Endpoint 1(in/out): Controller 1
			Interface 2:
  			Endpoint 3(in/out): Controller 2
			Interface 4:
  			Endpoint 5(in/out): Controller 3
			Interface 6:
  			Endpoint 7(in/out): Controller 4
		 */

		switch (device.getVendorId())
		{
		case 0x045e: //vendor: Microsoft
			switch (device.getProductId())
			{
			case 0x028e: //wired controller
			{
				if (addXpad(device, device.getInterface(0), false))
					Log.d(TAG, "added Xpad "+ device.getDeviceName());

				return true;
			}
			case 0x0291: //wireless dongle
			case 0x0791: //360 wireless dongle
			{
				addXpad(device, device.getInterface(0), true);//Controller 	1
				addXpad(device, device.getInterface(2), true);//			2
				addXpad(device, device.getInterface(4), true);//			3
				addXpad(device, device.getInterface(6), true);//			4
				return true;
			}
			default: 
				return false;
			}
		case 0x0f0d: //Hori Fighting stick EX2
			if (device.getProductId()==0x0016)
				addXpad(device, device.getInterface(0), false);
			return true;
		default:
			return false;
		}
	}

	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				probeXpad(device);
			}
			else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				for(XpadDevice d : mXpadDevices)
					if (d.mDevice.equals(device)) {
						d.XpadStop();
					}
			}
		}
	};
}
