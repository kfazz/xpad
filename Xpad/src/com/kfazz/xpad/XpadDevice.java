package com.kfazz.xpad;

import java.nio.ByteBuffer;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class XpadDevice implements Runnable {

	private static final String TAG = "XpadDevice";

	private Activity mActivity; // handle to calling activity
	public final UsbDevice mDevice; // handle to Usb device, used for guarding against
								//adding a device twice.
	private UsbDeviceConnection mConnection; // device connection
	private UsbInterface mInterface; //The interface
	private boolean isWireless=false; // is controller wireless?

	private UsbEndpoint epIn, epOut; //endpoints for comms

	//for controlling the reader thread
	private boolean running = false;

	// which led should be lit on the controller
	private int mPlayerNum = 0;

	static private int numplayers=0;

	//wireless only
	int batt_level;

	XpadDevice(Activity activity, UsbDevice device, UsbDeviceConnection connection,
			UsbInterface intf, boolean wireless, int playerNum)
			{
		mActivity = activity;

		mDevice = device;
		
		mConnection = connection;
		mInterface = intf;
		isWireless = wireless; //usb device is dongle
		mPlayerNum = playerNum;

		epIn = epOut = null;
			}

	public void XpadStart()
	{
		Log.d(TAG, "in start(), isWireless " + isWireless + " player #" + mPlayerNum);
		epIn  = mInterface.getEndpoint(0); // Controller events
		epOut = mInterface.getEndpoint(1); //messages to controller

		running = true; // allow thread to run
		Thread thread = new Thread(this); 
		thread.start();

		numplayers++;
	}

	public void XpadStop()
	{
		//if we're called from usb disco event, we can't turn off controller, but at least stop the thread...
		running = false; //stop reading from controller
		powerDown(); //turn off controller if it's wireless
		numplayers--;
	}

	boolean isAlive()
	{
		return running; //return current state in case
		//game wants to skip a wireless port that doesn't have a controller paired to it
	}
	public int num_players()
	{
		return numplayers;
	}

	//Input report:
	/*
	 * Offset	 Length (bits)	 Description	 Windows driver
0x00.0	 8	 Message type
0x01.0	 8	 Packet size (20 bytes = 0x14)
0x02.0	 1	 D-Pad up	 D-Pad up
0x02.1	 1	 D-Pad down	 D-Pad down
0x02.2	 1	 D-Pad left	 D-Pad left
0x02.3	 1	 D-pad right	 D-Pad right
0x02.4	 1	 Start button	 Button 8
0x02.5	 1	 Back button	 Button 7
0x02.6	 1	 Left stick press	 Button 9
0x02.7	 1	 Right stick press	 Button 10
0x03.0	 1	 Button LB	 Button 5
0x03.1	 1	 Button RB	 Button 6
0x03.2	 1	 Xbox logo button
0x03.3	 1	 Unused
0x03.4	 1	 Button A	 Button 1
0x03.5	 1	 Button B	 Button 2
0x03.6	 1	 Button X	 Button 3
0x03.7	 1	 Button Y	 Button 4
0x04.0	 8	 Left trigger	 Z-axis down
0x05.0	 8	 Right trigger	 Z-axis up
0x06.0	 16	 Left stick X-axis	 X-axis
0x08.0	 16	 Left stick Y-axis	 Y-axis
0x0a.0	 16	 Right stick X-axis	 X-turn
0x0c.0	 16	 Right stick Y-axis	 Y-turn
0x0e.0	 48	 Unused */

	void passevent(final XpadEventMsg msg)
	{
		//Log.d(TAG, "passing event to View on UI thread");
		//We're still in Non UI context, need to call into View object from UI thread
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				//if(((GameView) mActivity.findViewById(R.id.game)).hasFocus())
					((GameView) mActivity.findViewById(R.id.game)).onXpadMotionEvent(msg);
			}
		});		
	}

	void parseWired(byte[] buffer)
	{
		//for Wired controller we only care about event msgs for now
		//Log.d(TAG, "parseWired buffer:" + buffer.length + "buffer[1]:" + buffer[1]);
		switch (buffer.length) {
		case 32: {
			if (buffer[0]==0x00 && buffer[1]==0x14) { //Event Message
				XpadEventMsg event =  new XpadEventMsg(buffer[2], buffer[3], buffer[4], buffer[5],
						((( 128+(int)buffer[7])<<8)|(0xFF&(int)buffer[6])),//promote the bytes to ints
						((( 128+(int)buffer[9])<<8)|(0xFF&(int)buffer[8])),//strip the sign
						((( 128+(int)buffer[11])<<8)|(0xFF&(int)buffer[10])),//shift highbyte left
						((( 128+(int)buffer[13])<<8)|(0xFF&(int)buffer[12])), this, mPlayerNum);//or with lobyte
				passevent(event); 
			}
		}
		default:
			return;
		}
	}

	void parseWireless(byte[] buffer)
	{
		switch (buffer[0])
		{
		case 0x00:
			if ((buffer[1] ==(byte) 0x0f) && (buffer[2] == 0x00) && (buffer[3] == (byte)0xf0)) {
				Log.d(TAG, "Controller Announce");
				Log.d(TAG, "Battery: " + ((int)buffer[17] & 0xFF)); //bytes are signed in java...
				batt_level = ((int)buffer[17] & 0xFF);
				Log.d(TAG, "setting led " + this.mPlayerNum );
				ledCommand(this.mPlayerNum);
			}
			if ((buffer[1] == 0x01) && (buffer[2] == 0x00) && (buffer[3] == (byte)0xf0)
					&& (buffer[4] == 0x00) && (buffer[5] == 0x13)) {
				//Event packet same as wired response packet just need to skip 3 more bytes
				XpadEventMsg event =  new XpadEventMsg(buffer[6], buffer[7], buffer[8], buffer[9],
						(double)((( 128+(int)buffer[11])<<8)|(0xFF&(int)buffer[10])),//promote the bytes to ints
						(double)((( 128+(int)buffer[13])<<8)|(0xFF&(int)buffer[12])),//strip the sign
						(double)((( 128+(int)buffer[15])<<8)|(0xFF&(int)buffer[14])),//shift highbyte left
						(double)((( 128+(int)buffer[17])<<8)|(0xFF&(int)buffer[16])), this, mPlayerNum);//or with lobyte
				passevent(event);
				//Log.d(TAG, "Event resp.");

			}
			if ((buffer[1] == 0x00) && (buffer[2] == 0x00) && (buffer[3] == 0x13)) {
				Log.d(TAG, "Battery: " + ((int)buffer[4] & 0xFF));
				batt_level = ((int)buffer[4] & 0xFF);
			}
			if ((buffer[1] == 0x00) && (buffer[2] == 0x00) && (buffer[3] == (byte)0xf0)) {
				//Log.d(TAG, "Null Resp.");
			}
			break;
		case 0x08:
			if (buffer[1]==0x00)
			{
				Log.d(TAG, "CSM: Nothing Connected."); 
				numplayers--;
			}
			else if (buffer[1]==0x40) 
			{
				Log.d(TAG, "CSM: Headset Only.");
				numplayers--;
			}
			else if (buffer[1]==(byte)0x80)
				Log.d(TAG, "CSM: Controller.");
			else if (buffer[1]==(byte)0xc0) 
				Log.d(TAG, "CSM: Controller + headset.");
			break;

		default:
			return;	
		}
	}

	public void ledCommand(int num)
	{
		if (isWireless)
		{
			byte[] bytes = {0x00, 0x00, 0x08, (byte) (0x40 +((num + 5) % 0x0e)),0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
			mConnection.bulkTransfer(epOut, bytes, bytes.length, 500); //synchronous bulk transfer 500ms timeout
		}	
		else {
			byte[] bytes = {0x01,0x03,(byte)(num + 5)};
			mConnection.bulkTransfer(epOut, bytes, bytes.length, 500); //synchronous bulk transfer 500ms timeout
		}
	}

	public void rumble (byte left, byte right)
	{
		if (isWireless) {
			byte bytes[] = { 0x00, 0x01, 0x0f, (byte)0xc0, 0x00, left, right, 0x00, 0x00, 0x00, 0x00, 0x00 };
			mConnection.bulkTransfer(epOut, bytes, bytes.length, 500); //synchronous bulk transfer 500ms timeout
		}
		else
		{
			byte bytes[] = { 0x00, 0x08, 0x00, left,  right, 0x00, 0x00, 0x00 };
			mConnection.bulkTransfer(epOut, bytes, bytes.length, 500); //synchronous bulk transfer 500ms timeout
		}
	}

	public void powerDown()
	{
		if (isWireless)
		{
			byte [] bytes = { 00, 00, 0x08,(byte) 0xc0, 00, 00, 00, 00, 00, 00, 00, 00 };
			Log.d(TAG, "Powering down Controller #" + mPlayerNum);
			mConnection.bulkTransfer(epOut, bytes, bytes.length, 500); //synchronous bulk transfer 500ms timeout
			return;
		}
		Log.d(TAG, "Attempting to power down wired controller, doing nothing.");
		return;
	}

	@Override 
	public void run()
	{
		ledCommand(this.mPlayerNum);
		//rumble((byte)0x255, (byte)0x255);

		int bufferDataLength = epIn.getMaxPacketSize();
		Log.d(TAG, "Max Packet size:" + bufferDataLength);
		ByteBuffer buffer = ByteBuffer.allocate(bufferDataLength); //+1?

		UsbRequest requestQueued = null;
		UsbRequest request = new UsbRequest();
		request.initialize(mConnection, epIn);
		do
		{
			request.queue(buffer, bufferDataLength);
			requestQueued = mConnection.requestWait();

			if (request.equals(requestQueued))
			{
				byte[] byteBuffer = new byte[buffer.remaining()];
				buffer.get(byteBuffer);
				
				//Log.d(TAG, "Xpad #" + mPlayerNum + " Rx'd Msg Size:" + byteBuffer.length);

				//for (int i =0;i<byteBuffer.length;i++)
				//	Log.d(TAG,"bytebuffer[" + i + "]:" + byteBuffer[i]);

				if (isWireless)
					parseWireless(byteBuffer);
				else
					parseWired(byteBuffer);
				buffer.clear();
			}
			else
			{
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					Log.d(TAG, "Thread Interrupted.");
				}
			}
		} while (running);
		
		Log.d(TAG,"Controller #" + mPlayerNum +"'s Reader thread dying.");
		request.cancel();
		request.close();
		return;
	}
}
