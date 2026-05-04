package com.yournal.testutil;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LiveDataTestUtil {
    private LiveDataTestUtil() {}

    public static <T> T getOrAwaitValue(LiveData<T> liveData) throws InterruptedException {
        return getOrAwaitValue(liveData, 2, TimeUnit.SECONDS);
    }

    public static <T> T getOrAwaitValue(LiveData<T> liveData, long time, TimeUnit unit) throws InterruptedException {
        final Object[] data = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);

        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T value) {
                data[0] = value;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };

        liveData.observeForever(observer);

        if (!latch.await(time, unit)) {
            liveData.removeObserver(observer);
            throw new AssertionError("LiveData value was never set.");
        }

        //noinspection unchecked
        return (T) data[0];
    }
}
