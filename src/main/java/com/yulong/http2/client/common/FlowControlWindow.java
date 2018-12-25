package com.yulong.http2.client.common;

import static com.yulong.http2.client.utils.Debug.debugFlowControl;
import static com.yulong.http2.client.utils.LogUtil.log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The window used for flow control
 */
public class FlowControlWindow {

	private final int streamId;
	private long availableSize;
	private int initialSize;

	private final Lock lock = new ReentrantLock();
	private final Condition availableCondition = lock.newCondition();

	public FlowControlWindow(int streamId, int initialSize) {
		this.streamId = streamId;
		this.availableSize = this.initialSize = initialSize;
	}

	/**
	 * Wait until the available size is larger or equal to the specified size
	 * 
	 * @param requiredMinSize the required minimal size
	 * @return
	 */
	public int availableSize(int requiredMinSize) {
		debugFlowControl(this, "before checking availability");
		lock.lock();
		while (availableSize < requiredMinSize) {
			debugFlowControl(this, "wait until available size larger than " + requiredMinSize);
			availableCondition.awaitUninterruptibly();
		}
		debugFlowControl(this, "before sending");
		return (int) availableSize;

	}

	public void consume(int size) {
		this.availableSize -= size;
		lock.unlock();
		debugFlowControl(this, "after sent");
	}

	/**
	 * Increment the window size by delta.
	 * This is usually called when WINDOW_UPDATE frame is received
	 * 
	 * @param delta
	 * @return boolean true for success and false for failure
	 */
	public boolean increment(int delta) {
		debugFlowControl(this, "before window increment");
		boolean success = true;
		lock.lock();
		try {
			availableSize += delta;
			if (availableSize > Integer.MAX_VALUE) {
				log("The window size (" + availableSize + ") exceeds the max value");
				success = false;
			}
			availableCondition.signal();
		} finally {
			lock.unlock();
		}
		debugFlowControl(this, "after window increment");
		return success;
	}

	/**
	 * Resize the window size. This is usually called when SETTINGS frame is 
	 * received and contains INITIAL_WINDOW_SIZE
	 * 
	 * @param newSize
	 */
	public void resize(int newSize) {
		debugFlowControl(this, "before resize");
		int delta = newSize - (initialSize - (int) availableSize);
		increment(delta);
		this.initialSize = newSize;
		debugFlowControl(this, "after resize");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Flow-Control Window [").append(streamId);
		sb.append("] intialSize=").append(initialSize);
		sb.append("; windowSize=").append(availableSize);
		return sb.toString();
	}

}
