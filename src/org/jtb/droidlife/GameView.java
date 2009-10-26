package org.jtb.droidlife;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

/**
 * View that draws, takes keystrokes, etc. for a simple LunarLander game.
 * 
 * Has a mode which RUNNING, PAUSED, etc. Has a x, y, dx, dy, ... capturing the
 * current ship physics. All x/y etc. are measured with (0,0) at the lower left.
 * updatePhysics() advances the physics based on realtime. draw() renders the
 * ship, and does an invalidate() to prompt another draw() as soon as possible
 * by the system.
 */
class GameView extends SurfaceView implements SurfaceHolder.Callback {
	private class GameThread extends Thread {
		/** Message handler used by thread to post stuff back to the GameView */
		private Handler mHandler;

		/** Indicate whether the surface has been created & is ready to draw */
		private boolean mRun = false;
		/** Handle to the surface manager object we interact with */
		private SurfaceHolder mSurfaceHolder;

		private World mWorld;

		public GameThread(SurfaceHolder surfaceHolder, Context context,
				World world, Handler handler) {

			mSurfaceHolder = surfaceHolder;
			mHandler = handler;
			mContext = context;
			mWorld = world;
		}

		@Override
		public void run() {
			mRun = true;
			while (mRun) {
				Canvas c = null;
				try {
					c = mSurfaceHolder.lockCanvas(null);
					synchronized (mSurfaceHolder) {
						mWorld.generate();
						c.drawARGB(255, 0, 0, 0);
						mWorld.draw(c);

						mActivityHandler.sendMessage(mActivityHandler
								.obtainMessage(GameActivity.UPDATE_GEN_WHAT,
										mWorld.getGeneration()));
						mActivityHandler.sendMessage(mActivityHandler
								.obtainMessage(GameActivity.UPDATE_POP_WHAT,
										mWorld.getPopulation()));
					}
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (c != null) {
						mSurfaceHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}

		public void setRunning(boolean b) {
			mRun = b;
		}

		public boolean isRunning() {
			return mRun;
		}
	}

	private Context mContext;
	private GameThread thread;
	private int mCanvasWidth;
	private int mCanvasHeight;
	private World mWorld;
	private SurfaceHolder mSurfaceHolder;
	private Prefs prefs;
	private Handler mActivityHandler;

	public GameView(Context context, AttributeSet attrs) {
		super(context, attrs);

		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		setFocusable(true); // make sure we get key events
		prefs = new Prefs(context);
	}

	/**
	 * Fetches the animation thread corresponding to this LunarView.
	 * 
	 * @return the animation thread
	 */
	public GameThread getThread() {
		return thread;
	}

	/**
	 * Standard window-focus override. Notice focus lost so we can pause on
	 * focus lost. e.g. user switches to take a call.
	 */
	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
	}

	/* Callback invoked when the surface dimensions change. */
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		mCanvasWidth = width;
		mCanvasHeight = height;
	}

	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceHolder = holder;
		refresh();
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		stop();
	}

	public void stop() {
		if (thread != null) {
			// we have to tell thread to shut down & wait for it to finish, or
			// else
			// it might touch the Surface after we return and explode
			boolean retry = true;
			thread.setRunning(false);
			while (retry) {
				try {
					thread.join();
					retry = false;
				} catch (InterruptedException e) {
				}
			}
			thread = null;
		}
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_STATUS_WHAT, false));
	}

	public boolean isSeeded() {
		return mWorld != null;
	}

	public void start() {
		thread = new GameThread(getHolder(), mContext, mWorld, new Handler() {
			@Override
			public void handleMessage(Message m) {
				// Use for pushing back messages.
			}
		});
		thread.start();
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_STATUS_WHAT, true));
	}

	public void seed(Seeder seeder) {
		int cellSize = prefs.getCellSize();
		int[] birthNeighbors = prefs.getBirthRule();
		int[] surviveNeighbors = prefs.getSurvivalRule();
		mWorld = new World(mCanvasWidth / cellSize, mCanvasHeight / cellSize,
				cellSize, birthNeighbors, surviveNeighbors);
		seeder.seed(mWorld);
		Canvas c = null;
		try {
			c = mSurfaceHolder.lockCanvas(null);
			synchronized (mSurfaceHolder) {
				c.drawARGB(255, 0, 0, 0);
				mWorld.draw(c);
			}
		} finally {
			if (c != null) {
				mSurfaceHolder.unlockCanvasAndPost(c);
			}
		}
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_TYPE_WHAT, mWorld.getType()));
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_GEN_WHAT, mWorld.getGeneration()));
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_POP_WHAT, mWorld.getPopulation()));
	}

	public void refresh() {
		if (mWorld == null) {
			return;
		}
		Canvas c = null;
		try {
			c = mSurfaceHolder.lockCanvas(null);
			synchronized (mSurfaceHolder) {
				c.drawARGB(255, 0, 0, 0);
				mWorld.draw(c);
			}
		} finally {
			if (c != null) {
				mSurfaceHolder.unlockCanvasAndPost(c);
			}
		}
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_STATUS_WHAT, isRunning()));
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_TYPE_WHAT, mWorld.getType()));
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_GEN_WHAT, mWorld.getGeneration()));
		mActivityHandler.sendMessage(mActivityHandler.obtainMessage(
				GameActivity.UPDATE_POP_WHAT, mWorld.getPopulation()));
	}

	public boolean isRunning() {
		return thread != null;
	}

	public void setActivityHandler(Handler mActivityHandler) {
		this.mActivityHandler = mActivityHandler;
	}
}