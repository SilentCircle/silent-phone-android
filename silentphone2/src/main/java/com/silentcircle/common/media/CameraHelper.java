/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.common.media;

import android.app.Activity;
import android.graphics.Point;
import android.hardware.Camera;
import android.view.Display;
import android.view.Surface;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera related utilities.
 */
@SuppressWarnings("deprecation")
public class CameraHelper {
    /**
     * @return a mapping of all camera ids to matching camera info
     */
    public static Map<Integer, Camera.CameraInfo> getCameras() {
        Map<Integer, Camera.CameraInfo> cameraMap = new HashMap<Integer, Camera.CameraInfo>();

        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = null;

        for(int i = 0; i < numberOfCameras; i++) {
            cameraInfo = new Camera.CameraInfo();

            Camera.getCameraInfo(i, cameraInfo);

            cameraMap.put(i, cameraInfo);
        }

        return cameraMap;
    }

    /**
     * @return the default camera on the device. Return null if there is no camera on the device.
     */
    public static Camera getDefaultCameraInstance() {
        return Camera.open();
    }

    private static Point getDefaultDisplaySize(Activity activity, Point size) {
        Display d = activity.getWindowManager().getDefaultDisplay();
        d.getSize(size);

        return size;
    }

    public static Camera.Size getOptimalPreviewSize2(List<Camera.Size> sizes, double targetRatio) {
        final double SMALL_VALUE = 0.001;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double optimalRatio = 0;
        double minRatioDiff = Double.MAX_VALUE;

        // Try to find a size which is close to the targetRatio and has the biggest possible resolution
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(targetRatio - ratio) <= minRatioDiff) {
                if (optimalSize != null && Math.abs(ratio - optimalRatio) < SMALL_VALUE) {
                    if (size.width < optimalSize.width) continue;
                }
                optimalSize = size;
                optimalRatio = (double) size.width/size.height;
                minRatioDiff = Math.abs(targetRatio - ratio);
            }
        }
        return optimalSize;
    }

    /**
     * Returns a picture size that its aspect ratio is close to the provided {@code targetRatio} and its
     * height is not bigger than 2700 pixels.
     */
    public static Camera.Size getOptimalPictureSize(List<Camera.Size> sizes, double targetRatio) {
        final double SMALL_VALUE = 0.001;
        if (sizes == null) return null;
        // Find the closest match
        Camera.Size closestMatch = getOptimalPreviewSize2(sizes, targetRatio);
        targetRatio = (float)closestMatch.width/closestMatch.height;
        // Find all the sizes that have the same aspect ratio
        List<Camera.Size> selectedSizes = new ArrayList<>();
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(targetRatio - ratio) < SMALL_VALUE) {
                selectedSizes.add(size);
            }
        }
        if (selectedSizes.isEmpty()) {
            return null;
        }
        sortCameraSizes(selectedSizes);
        // Pick one size that has not very big height
        Camera.Size optimalSize = null;
        for (Camera.Size size : selectedSizes) {
            if (size.height > 2700) {
                continue;
            }
            else {
                optimalSize = size;
                break;
            }
        }
        if (optimalSize == null) {
            optimalSize = selectedSizes.get(0);
        }
        return optimalSize;
    }

    public static Camera.Size getOptimalPreviewSize(Activity currentActivity,
                                                    List<Camera.Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of preview surface. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size.
        Point point = getDefaultDisplaySize(currentActivity, new Point());
        int targetHeight = Math.min(point.x, point.y);
        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public static int getOrientationHint(Activity activity, int cameraId) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * Sorts the camera sizes in decreasing order.
     *
     * Camera sizes with the same amount of pixel are compared according to their width.
     */
    public static void sortCameraSizes(List<Camera.Size> sizes) {
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                int diff = lhs.width * lhs.height - rhs.width * rhs.height;
                if (diff == 0) {
                    diff = (lhs.width > rhs.width) ? 1 : -1;
                }
                return -diff;
            }
        });
    }

}
