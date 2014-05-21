package com.kfazz.xpad;

public class XpadEventMsg {
	/* Offset	 Length (bits)	 Description	 Windows driver
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

	private boolean dup, ddown, dleft, dright;
	private boolean start, back, ls, rs; //left and right stick press events
	private boolean lb, rb, xb; //left shoulder button, right shoulder button, X buton
	private boolean a,b,x,y;
	private int lt, rt; //left trigger, right trigger;
	private double lx, ly; //Left joystick
	private double rx, ry; //Right joystick
	private XpadDevice mDevice; //handle to device that sent this event, allows game to callback
	//for rumble , led, or turning off controller
	private int mPlayerNum;

	public XpadEventMsg(byte two, byte three, byte four, byte five, double lx, double ly, double rx, double ry, XpadDevice dev, int pId)
	{
		mPlayerNum = pId;
		mDevice = dev;
		dup=ddown=dleft=dright=start=back=ls=rs=lb=rb=xb=a=b=x=y=false;
		int i;
		i = (int)(two & 0xFF);
		if ((i & (1 << 0))!=0)
			dup = true;
		if ((i & (1 << 1))!=0)
			ddown = true;
		if ((i & (1 << 2))!=0)
			dleft = true;
		if ((i & (1 << 3))!=0)
			dright = true;
		if ((i & (1 << 4))!=0)
			start = true;
		if ((i & (1 << 5))!=0)
			back = true;
		if ((i & (1 << 6))!=0)
			ls = true;
		if ((i & (1 << 7))!=0)
			rs = true;

		i = (int)(three & 0xFF);
		if ((i & (1 << 0))!=0)
			lb = true;
		if ((i & (1 << 1))!=0)
			rb= true;
		if ((i & (1 << 2))!=0)
			xb = true;
		//if ((i & (1 << 3))!=0)
		//dummy bit
		if ((i & (1 << 4))!=0)
			a = true;
		if ((i & (1 << 5))!=0)
			b = true;
		if ((i & (1 << 6))!=0)
			x = true;
		if ((i & (1 << 7))!=0)
			y = true;

		//triggers
		lt = (int)(four & 0xFF);
		rt = (int)(five & 0xFF);

		//sticks
		this.lx = lx;
		this.ly = ly;
		this.rx = rx;
		this.ry = ry;
	}

	public boolean get_d_up(){
		return dup;}
	public boolean get_d_down(){
		return ddown;}
	public boolean get_d_left(){
		return dleft;}
	public boolean get_d_right(){
		return dright;}
	public boolean get_back(){
		return back;}
	public boolean get_start(){
		return start;}
	public boolean get_X(){
		return xb;}
	public boolean get_a(){
		return a;}
	public boolean get_b(){
		return b;}
	public boolean get_x(){
		return x;}
	public boolean get_y(){
		return y;}
	public boolean get_rb(){
		return rb;}
	public boolean get_lb(){
		return lb;}
	public boolean get_ls(){
		return ls;}
	public boolean get_rs(){
		return rs;}
	public int get_lt(){
		return lt;}
	public int get_rt(){
		return rt;}
	public double get_lx(){
		return lx;}
	public double get_ly(){
		return ly;}
	public double get_rx(){
		return rx;}
	public double get_ry(){
		return ry;}
	public int get_id(){
		return mPlayerNum;
	}

	public void rumble (byte l, byte r)
	{
		mDevice.rumble(l, r);
	}


	public String toString()
	{
		String s = "";
		if (get_d_left())
			s += " D-Left";
		if (get_d_right())
			s += " D-Right";
		if (get_d_up())
			s += " D-Up";
		if (get_d_down())
			s += " D-Down";
		if (get_ls())
			s += " L-Stick";
		if (get_rs())
			s += " R-Stick";
		if (get_lb())
			s += " L-Shldr";
		if (get_rb())
			s += " R-Shldr";
		if (get_start())
			s += " Start";
		if (get_back())
			s += " Back";
		if (get_X())
			s += " X";
		if (get_a())
			s += " a";
		if (get_b())
			s += " b";
		if (get_x())
			s += " x";
		if (get_y())
			s += " y";
		if (get_lt()!=0)
			s +=" L-Trigger" + ((Integer)get_lt()).toString();
		if (get_rt()!=0)
			s +=" R-Trigger" + ((Integer)get_rt()).toString();
		if (get_lx()!=0)
			s +=" Left X" + ((Double)get_lx()).toString();
		if (get_ly()!=0)
			s +=" Left Y" + ((Double)get_ly()).toString();
		if (get_rx()!=0)
			s +=" Right X" + ((Double)get_rx()).toString();
		if (get_ry()!=0)
			s +=" Right Y" + ((Double)get_ry()).toString();
		return s;



	}


}
