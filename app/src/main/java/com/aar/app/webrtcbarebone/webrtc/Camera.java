package com.aar.app.webrtcbarebone.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Camera {
    private static final String TAG = "WebRTCCamera";

    public static final int VGA_VIDEO_WIDTH = 640;
    public static final int VGA_VIDEO_HEIGHT = 480;
    public static final int HD_720_VIDEO_WIDTH = 1280;
    public static final int HD_720_VIDEO_HEIGHT = 720;
    public static final int DEFAULT_VIDEO_FRAME_RATE = 30;

    public enum Facing {
        Front, Back, None
    }

    private Facing mCurrentFacing;
    private CameraVideoCapturer mCameraVideoCapturer;
    private int mCameraCount;
    private boolean mCapturing = false;

    public Camera(@NonNull Context context,
                  Facing preferredCameraFacing,
                  @Nullable CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        CameraEnumerator enumerator = getCameraEnumerator(context);
        mCameraCount = enumerator.getDeviceNames().length;
        mCameraVideoCapturer = getCameraVideoCapturer(enumerator, preferredCameraFacing, eventsHandler);
        mCurrentFacing = preferredCameraFacing;

        if (mCameraVideoCapturer == null) {
            if (preferredCameraFacing == Facing.Front) {
                mCameraVideoCapturer = getCameraVideoCapturer(enumerator, Facing.Back, eventsHandler);
                mCurrentFacing = Facing.Back;
            } else {
                mCameraVideoCapturer = getCameraVideoCapturer(enumerator, Facing.Front, eventsHandler);
                mCurrentFacing = Facing.Front;
            }
        }

        if (mCameraVideoCapturer == null) {
            mCurrentFacing = Facing.None;
        }
    }

    public void startCapture() {
        startCapture(HD_720_VIDEO_WIDTH, HD_720_VIDEO_HEIGHT, DEFAULT_VIDEO_FRAME_RATE);
    }

    public void startCapture(int videoWidth, int videoHeight, int frameRate) {
        if (mCameraVideoCapturer != null && !mCapturing) {
            mCameraVideoCapturer.startCapture(videoWidth, videoHeight, frameRate);
            mCapturing = true;
        }
    }

    public void stopCapture() {
        if (mCameraVideoCapturer != null && mCapturing) {
            try {
                mCapturing = false;
                mCameraVideoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.d(TAG, "Error stop capture: " + e);
            }
        }
    }

    public void changeCaptureFormat(int videoWidth, int videoHeight, int frameRate) {
        if (mCameraVideoCapturer != null && mCapturing) {
            mCameraVideoCapturer.changeCaptureFormat(videoWidth, videoHeight, frameRate);
        }
    }

    public void close() {
        if (mCameraVideoCapturer != null) {
            mCameraVideoCapturer.dispose();
            mCapturing = false;
        }
    }

    public void switchCamera() {
        if (mCameraVideoCapturer == null || mCameraCount < 2) {
            Log.d(TAG, "Can't switch camera, capturer: " + mCameraVideoCapturer + "camcount: " + mCameraCount);
            return;
        }

        mCameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean isFrontCamera) {
                mCurrentFacing = isFrontCamera ? Facing.Front : Facing.Back;
            }

            @Override
            public void onCameraSwitchError(String errorDescription) {
                Log.i(TAG, "Error switching camera: " + errorDescription);
            }
        });
    }

    public Facing getCurrentFacing() {
        return mCurrentFacing;
    }

    public CameraVideoCapturer getCameraVideoCapturer() {
        return mCameraVideoCapturer;
    }

    private CameraVideoCapturer getCameraVideoCapturer(CameraEnumerator enumerator,
                                                       Facing facing,
                                                       CameraVideoCapturer.CameraEventsHandler cameraEventsHandler) {
        for (String devName : enumerator.getDeviceNames()) {
            if ((facing == Facing.Front && enumerator.isFrontFacing(devName)) ||
                    (facing == Facing.Back && enumerator.isBackFacing(devName))) {
                return enumerator.createCapturer(devName, cameraEventsHandler);
            }
        }
        return null;
    }

    private CameraEnumerator getCameraEnumerator(Context context) {
        if (Camera2Enumerator.isSupported(context)) {
            return new Camera2Enumerator(context);
        }

        Log.i(TAG, "Camera 2 API is not supported");
        return new Camera1Enumerator(true);
    }
}
