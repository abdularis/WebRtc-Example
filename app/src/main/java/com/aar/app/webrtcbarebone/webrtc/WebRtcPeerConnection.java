package com.aar.app.webrtcbarebone.webrtc;

import android.content.Context;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;

public class WebRtcPeerConnection {
    private static final String TAG = "WebRtcPeerConnection";

    private static final PeerConnection.IceServer FALLBACK_STUN_SERVER =
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer();


    private Camera mCamera;
    private PeerConnection mPeerConnection;
    private VideoSource mLocalVideoSource;
    private VideoTrack mLocalVideoTrack;
    private AudioSource mLocalAudioSource;
    private AudioTrack mLocalAudioTrack;


    public WebRtcPeerConnection(
            @NonNull Context context,
            @NonNull EglBase eglBase,
            @NonNull Camera camera,
            @NonNull PeerConnectionFactory factory,
            @NonNull PeerConnection.Observer observer,
            @NonNull List<PeerConnection.IceServer> iceServers) {
        mCamera = camera;

        List<PeerConnection.IceServer> internalIceServers = new ArrayList<>(iceServers);
        internalIceServers.add(FALLBACK_STUN_SERVER);

        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(internalIceServers);

        initAudio(factory);
        initVideo(context, eglBase, camera, factory);

        MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(mLocalAudioTrack);
        if (mLocalVideoTrack != null) mediaStream.addTrack(mLocalVideoTrack);

        mPeerConnection = factory.createPeerConnection(configuration, observer);
        mPeerConnection.addStream(mediaStream);
    }

    private void initAudio(PeerConnectionFactory factory) {
        mLocalAudioSource = factory.createAudioSource(new MediaConstraints());
        mLocalAudioTrack = factory.createAudioTrack("ARDAMSa0", mLocalAudioSource);
        mLocalAudioTrack.setEnabled(false);
    }

    private void initVideo(Context context, EglBase eglBase, Camera camera, PeerConnectionFactory factory) {
        CameraVideoCapturer capturer = camera.getCameraVideoCapturer();
        if (capturer != null) {
            mLocalVideoSource = factory.createVideoSource(capturer.isScreencast());
            capturer.initialize(
                    SurfaceTextureHelper.create("Cap0", eglBase.getEglBaseContext()),
                    context,
                    mLocalVideoSource.getCapturerObserver()
            );
            mLocalVideoTrack = factory.createVideoTrack("ARDAMSv0", mLocalVideoSource);
            mLocalVideoTrack.setEnabled(false);
        }
    }

    public VideoTrack getLocalVideoTrack() {
        return mLocalVideoTrack;
    }

    public AudioTrack getLocalAudioTrack() {
        return mLocalAudioTrack;
    }

    public void flipCamera() {
        mCamera.switchCamera();
    }

    public void enableAudio(boolean enable) {
        mLocalAudioTrack.setEnabled(enable);
    }

    public void enableVideo(boolean enable) {
        if (mLocalVideoTrack != null) {
            mLocalVideoTrack.setEnabled(enable);
        }

        if (enable) {
            mCamera.startCapture();
        } else {
            mCamera.stopCapture();
        }
    }

    public SessionDescription createOfferSync(MediaConstraints mediaConstraints)
            throws WebRtcPeerConnectionError {
        SettableFuture<SessionDescription> future = new SettableFuture<>();

        mPeerConnection.createOffer(new FutureCreateSdpObserver(future), mediaConstraints);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new WebRtcPeerConnectionError(e);
        }
    }

    public SessionDescription createAnswerSync(MediaConstraints mediaConstraints)
        throws WebRtcPeerConnectionError {
        SettableFuture<SessionDescription> future = new SettableFuture<>();

        mPeerConnection.createAnswer(new FutureCreateSdpObserver(future), mediaConstraints);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new WebRtcPeerConnectionError(e);
        }
    }

    public PeerConnection getPeerConnection() {
        return mPeerConnection;
    }
    public void setLocalDescriptionSync(SessionDescription sdp) throws WebRtcPeerConnectionError {
        SettableFuture<Boolean> future = new SettableFuture<>();
        mPeerConnection.setLocalDescription(new FutureSetSdpObserver(future), sdp);

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new WebRtcPeerConnectionError(e);
        }
    }

    public void setRemoteDescriptionSync(SessionDescription sdp) throws WebRtcPeerConnectionError {
        SettableFuture<Boolean> future = new SettableFuture<>();
        mPeerConnection.setRemoteDescription(new FutureSetSdpObserver(future), sdp);

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new WebRtcPeerConnectionError(e);
        }
    }

    public boolean addIceCandidate(IceCandidate iceCandidate) {
        return mPeerConnection.addIceCandidate(iceCandidate);
    }

    public static void initialize(@NonNull Context applicationContext) {
        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions
                        .builder(applicationContext)
                        .createInitializationOptions();

        PeerConnectionFactory.initialize(initOptions);
    }

    public static class WebRtcPeerConnectionError extends Exception {
        WebRtcPeerConnectionError(String message) {
            super(message);
        }

        WebRtcPeerConnectionError(Throwable cause) {
            super(cause);
        }
    }

    private static class FutureCreateSdpObserver implements SdpObserver {

        private SettableFuture<SessionDescription> future;

        FutureCreateSdpObserver(SettableFuture<SessionDescription> future) {
            this.future = future;
        }

        @Override
        public void onCreateSuccess(SessionDescription sdp) {
            this.future.set(sdp);
        }

        @Override
        public void onCreateFailure(String error) {
            this.future.setError(new WebRtcPeerConnectionError(error));
        }

        @Override
        public void onSetSuccess() { }

        @Override
        public void onSetFailure(String s) { }
    }

    private static class FutureSetSdpObserver implements SdpObserver {

        private SettableFuture<Boolean> future;

        FutureSetSdpObserver(SettableFuture<Boolean> future) {
            this.future = future;
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) { }

        @Override
        public void onCreateFailure(String s) { }

        @Override
        public void onSetSuccess() {
            this.future.set(true);
        }

        @Override
        public void onSetFailure(String error) {
            this.future.setError(new WebRtcPeerConnectionError(error));
        }
    }
}
