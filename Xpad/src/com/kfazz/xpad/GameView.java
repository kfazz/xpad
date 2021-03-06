package com.kfazz.xpad;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A trivial joystick based physics game to demonstrate joystick handling.
 *
 * @see GameControllerInput
 */
public class GameView extends View {
	private static int gameMode;  /* Game mode variable.
								 * 0: stationary rock shooting
								 * 1: asteroid field avoidance
								 * 2: DRIFTMANIA!
								 */
	
	private int[][] colors = 	// Colors for players
								   {{255, 63, 255, 63},
									{255, 0, 91, 255},  //light blue
									{255, 255, 165, 0}, //orange
									{255, 173, 255, 7}, //yellow green
									{255, 255, 0, 0}};  //red
	
	private final long ANIMATION_TIME_STEP = 1000 / 60;
	private final int MAX_OBSTACLES = 12;

	private final int MAX_PLAYERS = 6;//really 5, playerid is indexed starting at 1
	//need to seperate player id and controller id, use max of
	// 4 actual players regardless of how the controllrs enumerate.

	private final long FIRE_DELAY = 100; //time between firings
	private final long M2_TRAVEL_SPEED = 120; //asteroid field travel speed ;-)
	private final float DECELLERATION_FACTOR = -.007f; //should be negative
	private final float REL_SPEED_FACTOR = .5f;
	
	private final Random mRandom;
	//Support multiple players
	private Ship[] mShips;
	//keep their scores
	private Long[] mScores;

	//don't allocate in onDraw method, eclipse complains
	private Paint scorePaint;

	private final List<Bullet> mBullets;
	
	//if there's time subclass these into different types ie: enemies
	//give them a move() method or something
	private final List<Obstacle> mObstacles;

	//last time a frame was drawn
	private long mLastStepTime;
	private float mShipSize;
	private float mMaxShipThrust;
	private float mMaxShipSpeed;
	private float mBulletSize;
	private float mBulletSpeed;

	private float mMinObstacleSize;
	private float mMaxObstacleSize;
	private float mMinObstacleSpeed;
	private float mMaxObstacleSpeed;
	
	private boolean backPress;
	int highScorePlayer;
	long highScore;
	

	public class MyRunnable implements Runnable {
		  private int mId;
		  public MyRunnable(int id) {
		    mId = id;
		  }

		  public void run() {
			if (mShips[mId]!=null && mShips[mId].last!=null)  
				mShips[mId].last.rumble((byte)0x00,  (byte)(0x00));
		  }
		}

	
	//Handler Postdelayed method runs runnable after certain amount of milliseconds
	private Handler handler = new Handler();

	private final Runnable mAnimationRunnable = new Runnable() {
		public void run() {
			animateFrame();
		}
	};

	public GameView(Context context, AttributeSet attrs) {
		super(context, attrs);

		mRandom = new Random();

		mShips = new Ship[MAX_PLAYERS];
		mScores = new Long[MAX_PLAYERS];
		scorePaint = new Paint();

		mBullets = new ArrayList<Bullet>();
		mObstacles = new ArrayList<Obstacle>();
		
		gameMode = 0;
		backPress = false;
		highScorePlayer = 0;
		highScore = 0;

		setFocusable(true);

		//base size is 5 "Density independent Pixels" 
		//a DIP is defined as 1 for a 160dpi display
		float baseSize = getContext().getResources().getDisplayMetrics().density * 5f;
		float baseSpeed = baseSize * 3;

		mShipSize = baseSize * 3;
		mMaxShipThrust = baseSpeed * 0.4f;
		mMaxShipSpeed = baseSpeed * 14;

		mBulletSize = baseSize;
		mBulletSpeed = baseSpeed * 16 * 2;

		mMinObstacleSize = baseSize * 2;
		mMaxObstacleSize = baseSize * 12;
		mMinObstacleSpeed = baseSpeed;
		mMaxObstacleSpeed = baseSpeed * 3;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		// Reset the game when the view changes size.
		reset();
	}


	private void reset() {
		for (int i = 0; i < MAX_PLAYERS; i++) {
			mShips[i]=null;
			if (mScores[i]!=null)
				mScores[i] = (long) 0;
		}
		highScorePlayer = 0;
		highScore = 0;
		mBullets.clear();
		mObstacles.clear();
	}

	public  void onXpadMotionEvent(XpadEventMsg msg)
	{
		//Log.d("GameView", "Recieved Xpad Motion Event from controller #" + msg.get_id());

		if(mShips[msg.get_id()] == null) {
			Log.d("GameView", "Adding new Ship for player:" + msg.get_id());
			mShips[msg.get_id()]= new Ship();
			mScores[msg.get_id()]= Long.valueOf(0);
		}

		int id = msg.get_id();
		mShips[id].last = msg; //update most recent msg
		if ( msg.get_d_left() || msg.get_d_right() || msg.get_d_up() || msg.get_d_down()) {//Dpad is pressed, ignore joystick
			if (msg.get_d_left())
				mShips[id].setHeadingX(-1); //left
			else if (msg.get_d_right())
				mShips[id].setHeadingX(1); //right
			else
				mShips[id].setHeadingX(0); //center
			if (msg.get_d_up())
				mShips[id].setHeadingY(-1);//up
			else if (msg.get_d_down())
				mShips[id].setHeadingY(1);//down
			else 
				mShips[id].setHeadingY(0);//center
		}

		//this would work if i change from booleans to ints...
		//mShip.setHeading((int)msg.get_d_right() - (int)msg.get_d_left(), msg.get_d_down() - msg.get_d_up());

		else
		{
			float x, y;
			if (msg.get_lx() == -32768)
				x = -1;	//skip div by zero
			else
				x = (float) ((float)(msg.get_lx()-32768.0)/32768.0);//get_lx is 0 - 65535 we need -1 to +1
			if (msg.get_ly() == -32768)
				y = -1;	//skip div by zero
			else
				y = (float) ((float)(msg.get_ly()-32768.0)/32768.0);//get_lx is 0 - 65535 we need -1 to +1

			if (pythag(x,y)<0.2)
				x=y=0; //20% circular deadzone

			mShips[id].setHeading(x, (y * -1)); // y axis is inverted
		}
		
		long elapsedTime = System.currentTimeMillis() - mShips[id].lastFire;

		int numPlayers = 0;
		for (int i = 1; i< MAX_PLAYERS; i++)
			if (mShips[i] !=null)
				numPlayers++;
		long delaymod = FIRE_DELAY * numPlayers;
				
		//fire with a
		if (msg.get_a()){
			if (mShips[id] != null  && elapsedTime > delaymod && !mShips[id].isDestroyed()){
				Bullet bullet = new Bullet(mShips[id], mBulletSpeed);
				mShips[id].lastFire = System.currentTimeMillis();
				mBullets.add(bullet);
			}
		}
		
		//support firing with right joystick
		else {
			float rx, ry;
			if (msg.get_rx() == -32768)
				rx = -1;	//skip div by zero
			else
				rx = (float) ((float)(msg.get_rx()-32768.0)/32768.0);//get_lx is 0 - 65535 we need -1 to +1
			if (msg.get_ry() == -32768)
				ry = -1;	//skip div by zero
			else
				ry = (float) ((float)(msg.get_ry()-32768.0)/32768.0 * -1);//get_lx is 0 - 65535 we need -1 to +1
		
			if (pythag(rx,ry)>0.3 && mShips[id] != null && elapsedTime > delaymod
					&& !mShips[id].isDestroyed()) {
					
				Bullet bullet = new Bullet(mShips[id], (float)Math.atan2(ry, rx), mBulletSpeed);
				mShips[id].lastFire = System.currentTimeMillis();
				mBullets.add(bullet);
			}
		}
		
		if(msg.get_back()){
			if(!backPress){
				if(gameMode >= 2)
					gameMode = 0;
				else 
					gameMode++;
				
				reset();
			}
			backPress = true;
		}
		else backPress = false;
		
		step(SystemClock.uptimeMillis());
	}
	

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		// Turn on and off animations based on the window focus.
		// Alternately, we could update the game state using the Activity onResume()
		// and onPause() lifecycle events.
		if (hasWindowFocus) {
			getHandler().postDelayed(mAnimationRunnable, ANIMATION_TIME_STEP);
			mLastStepTime = SystemClock.uptimeMillis();
		} else {
			getHandler().removeCallbacks(mAnimationRunnable);

			int numShips = MAX_PLAYERS;
			for (int i = 0; i< numShips; i++)
			{
				if (mShips[i] != null) {
					mShips[i].setHeading(0, 0);
					mShips[i].setVelocity(0, 0);
				}
			}
		}
		super.onWindowFocusChanged(hasWindowFocus);
	}

	void animateFrame() {
		long currentStepTime = SystemClock.uptimeMillis();
		step(currentStepTime);

		Handler handler = getHandler();
		if (handler != null) {
			handler.postAtTime(mAnimationRunnable, currentStepTime + ANIMATION_TIME_STEP);
			invalidate();
		}
	}

	private void step(long currentStepTime) {
		float tau = (currentStepTime - mLastStepTime) * 0.001f;
		mLastStepTime = currentStepTime;

		// Move the ship(s).
		int numShips = MAX_PLAYERS;
		for (int i = 1; i< numShips; i++)
		{
			if (mShips[i]!=null) {
				mShips[i].accelerate(tau, mMaxShipThrust, mMaxShipSpeed);
				if (!mShips[i].step(tau)) {
					mShips[i] = new Ship(); 
				}
			}
		}

		// Move the bullets.
		int numBullets = mBullets.size();
		for (int i = 0; i < mBullets.size(); i++) {
			if (i < mBullets.size())
			{
				final Bullet bullet = mBullets.get(i);
				if (!bullet.step(tau)) {
					mBullets.remove(i);
					i -= 1;
					numBullets -= 1;
				}
			}
		}

		// Move obstacles.
		int numObstacles = mObstacles.size();
		for (int i = 0; i < numObstacles; i++) {
			final Obstacle obstacle = mObstacles.get(i);
			if (!obstacle.step(tau)) {
				mObstacles.remove(i);
				i -= 1;
				numObstacles -= 1;
			}
		}

		// Check for collisions between bullets and obstacles.
		for (int i = 0; i < numBullets; i++) {
			final Bullet bullet = mBullets.get(i);
			for (int j = 0; j < numObstacles; j++) {
				final Obstacle obstacle = mObstacles.get(j);
				if (bullet.collidesWith(obstacle)) {
					if(mScores[bullet.getId()]!=null)//bullets persist after a player dies
						mScores[bullet.getId()]+=10;// 10 points
					
					//FIXME if rumble_enabled...
					mShips[bullet.getId()].last.rumble((byte)127,(byte)obstacle.mSize);
					MyRunnable rumbleOff = new MyRunnable(bullet.getId());
					handler.postDelayed(rumbleOff, 50);
					
					bullet.destroy();
					obstacle.destroy();
					break;
				}
			}
		}

		// Check for collisions between the ship and obstacles.
		for (int i = 0; i < numObstacles; i++) {
			final Obstacle obstacle = mObstacles.get(i);
			for (int j = 1; j< numShips; j++) 
			{
				if (mShips[j]!=null) {
					if (mShips[j].collidesWith(obstacle)) {
						mScores[j]=null;
						mShips[j].destroy();
						for (Bullet b : mBullets)
						{
							if (b.getId()==j)
								b.destroy();
						}
						mShips[j]=null;
						obstacle.destroy();

					}
				}
			}
		}

		// Spawn more obstacles offscreen when needed.
		// Avoid putting them right on top of the ship.
		int obstacleCap = MAX_OBSTACLES;  //TODO: This is very ghetto
		if(gameMode == 1)
			obstacleCap = (MAX_OBSTACLES * 2);
		
		OuterLoop: while (mObstacles.size() < obstacleCap) {
			final float minDistance = mShipSize * 4;
			float size = mRandom.nextFloat() * (mMaxObstacleSize - mMinObstacleSize)
					+ mMinObstacleSize;
			float positionX, positionY;
			int tries = 0;
			do {
				int edge;
				if (gameMode == 1)
					edge = 3;
				else
					edge = mRandom.nextInt(4);
				switch (edge) {
				case 0:
					positionX = -size;
					positionY = mRandom.nextInt(getHeight());
					break;
				case 1:
					positionX = getWidth() + size;
					positionY = mRandom.nextInt(getHeight());
					break;
				case 2:
					positionX = mRandom.nextInt(getWidth());
					positionY = -size;
					break;
				default:
					positionX = mRandom.nextInt(getWidth());
					positionY = getHeight() + size;
					break;
				}
				if (++tries > 10) {
					break OuterLoop;
				}
				//FIXME this restricts them from spawning near the center, need to
				//change to minDistance away from each active ship.
			} while (pythag(positionX - 0, positionY - 0) < minDistance);

			float direction = mRandom.nextFloat() * (float) Math.PI * 2;
			float speed = mRandom.nextFloat() * (mMaxObstacleSpeed - mMinObstacleSpeed)
					+ mMinObstacleSpeed;
			

				
			float velocityX = (float) Math.cos(direction) * speed;
			float velocityY = (float) Math.sin(direction) * speed;

			Obstacle obstacle;
			if(gameMode == 2) 
				obstacle = new Enemy();
			else
				obstacle = new Obstacle();
			
			obstacle.setPosition(positionX, positionY);
			obstacle.setSize(size);
			obstacle.setVelocity(velocityX, velocityY);
			mObstacles.add(obstacle);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// Draw the ship(s).
		int numShips = MAX_PLAYERS;
		for (int i = 0; i < numShips; i++){
			if (mShips[i] != null) {
				mShips[i].draw(canvas);
			}
		}

		// Draw bullets.
		int numBullets = mBullets.size();
		for (int i = 0; i < numBullets; i++) {
			final Bullet bullet = mBullets.get(i);
			bullet.draw(canvas);
		}

		// Draw obstacles.
		int numObstacles = mObstacles.size();
		for (int i = 0; i < numObstacles; i++) {
			final Obstacle obstacle = mObstacles.get(i);
			obstacle.draw(canvas);
		}

		
		//Draw the scores last, so they're on top
		int playercount = 0;
		for (int i = 0; i < numShips; i++){
			if (mScores[i]!=null) 
			{	
				
				scorePaint.setARGB(colors[i][0], colors[i][1], colors[i][2], colors[i][3]);
				scorePaint.setTextSize(20);
				canvas.drawText("Player " + i + "Score :" + mScores[i], 10, 25*i, scorePaint);
				if(mScores[i] > highScore){
					highScorePlayer = i;
					highScore = mScores[i];
				}
				playercount++;
			}
		}
		if(highScorePlayer != 0){
			scorePaint.setARGB(colors[highScorePlayer][0], colors[highScorePlayer][1], colors[highScorePlayer][2], colors[highScorePlayer][3]);
			canvas.drawText("Top: P" + highScorePlayer + ": " + highScore, 10, (playercount + 2) * 25, scorePaint);
		}
		//draw game mode for debugging
		scorePaint.setColor(Color.GREEN);
		scorePaint.setTextSize(20);
		canvas.drawText("Mode: " + GameView.gameMode , canvas.getWidth() - 100, 25, scorePaint);
	}

	static float pythag(float x, float y) {
		return (float) Math.sqrt(x * x + y * y);
	}

	static int blend(float alpha, int from, int to) {
		return from + (int) ((to - from) * alpha);
	}

	static void setPaintARGBBlend(Paint paint, float alpha,
			int a1, int r1, int g1, int b1,
			int a2, int r2, int g2, int b2) {
			paint.setARGB(blend(alpha, a1, a2), blend(alpha, r1, r2),
			blend(alpha, g1, g2), blend(alpha, b1, b2));
	}

	private abstract class Sprite {
		protected float mPositionX;
		protected float mPositionY;
		protected float mVelocityX;
		protected float mVelocityY;
		protected float mSize;
		protected boolean mDestroyed;
		protected float mDestroyAnimProgress;

		public void setPosition(float x, float y) {
			mPositionX = x;
			mPositionY = y;
		}

		public void setVelocity(float x, float y) {
			mVelocityX = x;
			mVelocityY = y;
		}

		public void setSize(float size) {
			mSize = size;
		}

		public float distanceTo(float x, float y) {
			return pythag(mPositionX - x, mPositionY - y);
		}

		public float distanceTo(Sprite other) {
			return distanceTo(other.mPositionX, other.mPositionY);
		}

		public boolean collidesWith(Sprite other) {
			// Really bad collision detection.
			return !mDestroyed && !other.mDestroyed
					&& distanceTo(other) <= Math.max(mSize, other.mSize)
					+ Math.min(mSize, other.mSize) * 0.5f;
		}

		public boolean isDestroyed() {
			return mDestroyed;
		}
		
		

		public boolean step(float tau) {
			float yOffset = 0;
			if(gameMode == 1)
				yOffset = M2_TRAVEL_SPEED;
			
			mPositionX += mVelocityX * tau;
			mPositionY += (mVelocityY + yOffset) * tau;

			if (mDestroyed) {
				mDestroyAnimProgress += tau / getDestroyAnimDuration();
				if (mDestroyAnimProgress >= 1.0f) {
					return false;
				}
			}
			return true;
		}

		public abstract void draw(Canvas canvas);

		public abstract float getDestroyAnimDuration();

		protected boolean isOutsidePlayfield() {
			final int width = GameView.this.getWidth();
			final int height = GameView.this.getHeight();
			return mPositionX < 0 || mPositionX >= width
					|| mPositionY < 0 || mPositionY >= height;
		}

		protected void wrapAtPlayfieldBoundary() {
			final int width = GameView.this.getWidth();
			final int height = GameView.this.getHeight();
			while (mPositionX <= -mSize) {
				mPositionX += width + mSize * 2;
			}
			while (mPositionX >= width + mSize) {
				mPositionX -= width + mSize * 2;
			}
			while (mPositionY <= -mSize) {
				mPositionY += height + mSize * 2;
			}
			while (mPositionY >= height + mSize) {
				mPositionY -= height + mSize * 2;
			}
		}

		public void destroy() {
			mDestroyed = true;
			step(0);
		}
	}

	private class Ship extends Sprite {
		private static final float CORNER_ANGLE = (float) Math.PI * 2 / 3;
		private static final float TO_DEGREES = (float) (180.0 / Math.PI);

		private float mHeadingX;
		private float mHeadingY;
		private float mHeadingAngle;
		private float mHeadingMagnitude;
		private final Paint mPaint;
		private final Path mPath;
		
		
		public XpadEventMsg last;
		public long lastFire;

		public Ship() {
			
			mPaint = new Paint();
			mPaint.setStyle(Style.FILL);

			setPosition(getWidth() * 0.5f, getHeight() * 0.5f);
			setVelocity(0, 0);
			setSize(mShipSize);

			mPath = new Path();
			mPath.moveTo(0, 0);
			mPath.lineTo((float)Math.cos(-CORNER_ANGLE) * mSize,
					(float)Math.sin(-CORNER_ANGLE) * mSize);
			mPath.lineTo(mSize, 0);
			mPath.lineTo((float)Math.cos(CORNER_ANGLE) * mSize,
					(float)Math.sin(CORNER_ANGLE) * mSize);
			mPath.lineTo(0, 0);
			
			lastFire = 0;
		}

		public void setHeadingX(float x) {
			mHeadingX = x;
			updateHeading();
		}

		public void setHeadingY(float y) {
			mHeadingY = y;
			updateHeading();
		}

		public void setHeading(float x, float y) {
			mHeadingX = x;
			mHeadingY = y;
			updateHeading();
		}

		private void updateHeading() {
			mHeadingMagnitude = pythag(mHeadingX, mHeadingY);
			if (mHeadingMagnitude > 0.1f) {
				mHeadingAngle = (float) Math.atan2(mHeadingY, mHeadingX);
			}
		}

		public void accelerate(float tau, float maxThrust, float maxSpeed) {
			float thrust = mHeadingMagnitude * maxThrust;
			if(mHeadingMagnitude > 0){
				mVelocityX += Math.cos(mHeadingAngle) * thrust;
				mVelocityY += Math.sin(mHeadingAngle) * thrust;
			}
			else{
				mVelocityX += mVelocityX * DECELLERATION_FACTOR;
				mVelocityY += mVelocityY * DECELLERATION_FACTOR;
			}

			final float speed = pythag(mVelocityX, mVelocityY);
			if (speed > maxSpeed) {
				final float scale = maxSpeed / speed;
				mVelocityX = mVelocityX * scale;
				mVelocityY = mVelocityY * scale;
			}
		}

		@Override
		public boolean step(float tau) {
			if (!super.step(tau)) {
				return false;
			}

			wrapAtPlayfieldBoundary();
			return true;
		}

		public void draw(Canvas canvas) {
			int mPlayerId = last.get_id();
			setPaintARGBBlend(mPaint, mDestroyAnimProgress,
					colors[mPlayerId][0], colors[mPlayerId][1], colors[mPlayerId][2], colors[mPlayerId][3],
					0, 100, 0, 0);

			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.translate(mPositionX, mPositionY);
			canvas.rotate(mHeadingAngle * TO_DEGREES);
			canvas.drawPath(mPath, mPaint);
			canvas.restore();
		}

		@Override
		public float getDestroyAnimDuration() {
			return 1.0f;
		}
	}

	//modified to track player ID for scorekeeping
	private class Bullet extends Sprite {
		private final Paint mPaint;
		//player id of the shooter
		private final int mPlayerId;

		@SuppressWarnings("unused")
		//this will be sued in the future by AI fired bullets
		public Bullet() {
			mPaint = new Paint();
			mPaint.setStyle(Style.FILL);
			setSize(mBulletSize);
			mPlayerId = 0;
		}
		
		public Bullet(Ship origin, float velocity){ //convenience for a button
			this(origin, origin.mHeadingAngle, velocity);
		}

		public Bullet(Ship origin, float angle, float velocity) {
			mPaint = new Paint();
			mPaint.setStyle(Style.FILL);
			mPlayerId = origin.last.get_id(); // this is an issue otherwise could be sprite
											  // just have to check type of sprite here
			
			float xPos = (float) (origin.mPositionX + Math.cos(angle)* origin.mSize);
			float yPos = (float) (origin.mPositionY + Math.sin(angle) * origin.mSize);
			setPosition(xPos,yPos);
			
			float xSpeed = (float) (REL_SPEED_FACTOR * origin.mVelocityX + Math.cos(angle) * velocity);
			float ySpeed = (float) (REL_SPEED_FACTOR * origin.mVelocityY + Math.sin(angle) * velocity);
			setVelocity(xSpeed,ySpeed);
			
			setSize(mBulletSize);
		}

		public final int getId(){
			return mPlayerId;
		}

		@Override
		public boolean step(float tau) {
			if (!super.step(tau)) {
				return false;
			}
			return !isOutsidePlayfield();
		}

		public void draw(Canvas canvas) {
			setPaintARGBBlend(mPaint, mDestroyAnimProgress,
					colors[mPlayerId][0], colors[mPlayerId][1], colors[mPlayerId][2], colors[mPlayerId][3],
					0, 255, 255, 255);
			
			/*
			setPaintARGBBlend(mPaint, mDestroyAnimProgress,
					255, 255, 255, (255/(mPlayerId+1)),
					0, 255, 255, 255);*/ //white bullets
			canvas.drawCircle(mPositionX, mPositionY, mSize, mPaint);
		}

		@Override
		public float getDestroyAnimDuration() {
			return 0.125f;
		}
	}

	private class Obstacle extends Sprite {
		protected final Paint mPaint;

		public Obstacle() {
			mPaint = new Paint();
			mPaint.setARGB(255, 127, 127, 255);
			mPaint.setStyle(Style.FILL);
		}

		@Override
		public boolean step(float tau) {
			if (!super.step(tau)) {
				return false;
			}
			wrapAtPlayfieldBoundary();
			return true;
		}

		public void draw(Canvas canvas) {
			setPaintARGBBlend(mPaint, mDestroyAnimProgress,
					255, 127, 127, 255,
					0, 255, 0, 0);
			canvas.drawCircle(mPositionX, mPositionY,
					mSize * (1.0f - mDestroyAnimProgress), mPaint);
		}

		@Override
		public float getDestroyAnimDuration() {
			return 0.25f;
		}
	}

	private class Enemy extends Obstacle
	{
		protected final Paint mPaint; //overrides parents, 
									  //final per instantiated class?
		public Enemy() {
		mPaint = new Paint();
		mPaint.setARGB(255, 127, 255, 127);
		mPaint.setStyle(Style.FILL_AND_STROKE);
	}
		@Override
		public boolean step(float tau) {
			//FIXME seek nearest player
			//min 

			float dist = 800; //TODO pick a better number :)
			int target = 0;
			for (int i = 1; i < MAX_PLAYERS; i++)
			{
				if (mShips[i]!=null)
				{
					float sdist = distanceTo(mShips[i].mPositionX, mShips[i].mPositionY);
					if (sdist<dist) //target in range?
					{
						dist = sdist;
						target = i;
					}
				}
			}
			
			//if target !=0 twiddle the velocity
			if (target !=0)
			{
				double darct = Math.atan2(mShips[target].mPositionY - mPositionY,mShips[target].mPositionX - mPositionX);
				
					mVelocityX = (float) ( Math.cos(darct) * mBulletSpeed /4); //FIXME
					mVelocityY = (float) ( Math.sin(darct) * mBulletSpeed /4);
			}
			if (!super.step(tau)) {
				return false;
			}
			return true;
		}
		
		@Override
		public void draw (Canvas canvas)
		{
			setPaintARGBBlend(mPaint, mDestroyAnimProgress,
					255, 127, 255, 127,
					0, 255, 0, 0);
			canvas.drawRect(mPositionX-mSize, mPositionY-mSize,
					mPositionX+mSize, mPositionY+mSize,mPaint);
		}
		
		public boolean collidesWith(Sprite other) {
			// Really bad collision detection.
			float enemyRadius = other.mSize * 0.5f;
			
			return other.mPositionX <= mPositionX+mSize  && other.mPositionX >= mPositionX-mSize &&
					other.mPositionY <= mPositionY+mSize && other.mPositionY <= mPositionY+mSize ;
			

		}
	}
}
