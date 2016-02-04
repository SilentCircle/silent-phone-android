/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.silentcircle.messaging.location;

import java.lang.ref.SoftReference;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

public class LocationObserver extends OnLocationUpdateListener {

    private static Location cachedLocation;

    public static Location getCachedLocation(Context context) {
        LocationObserver observer = new LocationObserver();
        observer.initializeLocation(context);
        return observer.getLocation();
    }

    public static long getLastLocationTick(long interval) {
        long now = System.currentTimeMillis();
        return now - now % interval;
    }

    private static Location getLocation(LocationManager manager, String provider) {
        if (manager != null && manager.isProviderEnabled(provider)) {
            return manager.getLastKnownLocation(provider);
        }
        return null;
    }

    private static boolean isCachedLocationSufficient(long since, float radius) {
        if (cachedLocation != null) {
            synchronized (cachedLocation) {
                if (cachedLocation.getTime() > since) {
                    if (cachedLocation.hasAccuracy()) {
                        if (cachedLocation.getAccuracy() <= radius) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void observe(Context context, final long since, final float radius, final long maximumTimeToWaitForFreshLocation, final OnLocationReceivedListener onLocationReceived) {

        if (isCachedLocationSufficient(since, radius)) {
            onLocationReceived.onLocationReceived(cachedLocation);
            return;
        }

        if (context == null) {
            onLocationReceived.onLocationUnavailable();
            return;
        }

        if (!LocationUtils.isLocationSharingAvailable(context)) {
            onLocationReceived.onLocationUnavailable();
            return;
        }

        final SoftReference<Context> contextReference = new SoftReference<Context>(context);

        final Timer timer = new Timer();

        final LocationObserver observer = new LocationObserver() {

            private boolean finished;

            @Override
            protected void onBetterLocationReceived(Location location) {
                super.onBetterLocationReceived(location);
                if (!finished && location.getTime() >= since && location.hasAccuracy() && location.getAccuracy() <= radius) {
                    finished = true;
                    timer.cancel();
                    stopObserving(contextReference.get());
                    onLocationReceived.onLocationReceived(location);
                }
            }

        };

        if (maximumTimeToWaitForFreshLocation > 0) {

            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    timer.cancel();
                    Context context = contextReference.get();
                    observer.stopObserving(context);
                    Location location = getCachedLocation(context);
                    if (location == null) {
                        location = PassiveLocationReceiver.getPassiveLocation();
                    }
                    if (location != null) {
                        onLocationReceived.onLocationReceived(location);
                    } else {
                        onLocationReceived.onLocationUnavailable();
                    }
                }

            }, maximumTimeToWaitForFreshLocation);

        }

        observer.startObserving(context);

    }

    public static void observe(Context context, OnLocationReceivedListener onLocationReceived) {
        observe(context, getLastLocationTick(LocationComparator.DEFAULT_TIME_WINDOW), LocationComparator.DEFAULT_ACCURACY_WINDOW, LocationComparator.DEFAULT_TIME_WINDOW / 2, onLocationReceived);
    }

    private Location location;
    private final float minimumDistanceInMetersBetweenUpdates;

    private final long minimumTimeInMillisecondsBetweenUpdates;

    public LocationObserver() {
        this(LocationComparator.DEFAULT_TIME_WINDOW / 2, LocationComparator.DEFAULT_ACCURACY_WINDOW);
    }

    public LocationObserver(long minimumTimeInMillisecondsBetweenUpdates, float minimumDistanceInMetersBetweenUpdates) {
        this.minimumTimeInMillisecondsBetweenUpdates = minimumTimeInMillisecondsBetweenUpdates;
        this.minimumDistanceInMetersBetweenUpdates = minimumDistanceInMetersBetweenUpdates;
    }

    public Location getLocation() {
        return location;
    }

    private void initializeLocation(Context context) {
        initializeLocation((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
    }

    private void initializeLocation(LocationManager manager) {
        updateLocation(manager, LocationManager.GPS_PROVIDER);
        updateLocation(manager, LocationManager.NETWORK_PROVIDER);
        updateLocation(manager, LocationManager.PASSIVE_PROVIDER);
    }

    @Override
    protected void onBetterLocationReceived(Location location) {
        this.location = location;
        cachedLocation = location;
    }

    public void startObserving(Context context) {
        startObserving((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
    }

    public void startObserving(LocationManager manager) {
        startObserving(manager, LocationManager.GPS_PROVIDER);
        startObserving(manager, LocationManager.NETWORK_PROVIDER);
        startObserving(manager, LocationManager.PASSIVE_PROVIDER);
    }

    public void startObserving(LocationManager manager, String provider) {
        if (manager != null && manager.isProviderEnabled(provider)) {
            updateLocation(manager, provider);
            manager.requestLocationUpdates(provider, minimumTimeInMillisecondsBetweenUpdates, minimumDistanceInMetersBetweenUpdates, this);
        }
    }

    public void stopObserving(Context context) {
        if (context != null) {
            stopObserving((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
        }
    }

    public void stopObserving(LocationManager manager) {
        if (manager != null) {
            manager.removeUpdates(this);
        }
    }

    private void updateLocation(LocationManager manager, String provider) {
        onLocationChanged(getLocation(manager, provider));
    }

}
