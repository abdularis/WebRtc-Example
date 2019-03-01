package com.aar.app.webrtcbarebone;

import androidx.appcompat.app.AppCompatActivity;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.aar.app.webrtcbarebone.webrtc.Camera;
import com.aar.app.webrtcbarebone.webrtc.WebRtcPeerConnection;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CallActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_NAME = "ROOM_NAME";
    public static final String EXTRA_IS_MAKING_CALL = "CREATE_NEW_ID";

    private static final String TAG = "CallActivity";
    private static final String WEB_SOCKET_URL = "http://35.187.238.244:3000";

    private static final String EVENT_CREATE_ID = "/api/create";
    private static final String EVENT_OFFER_CALL = "/api/offerCall";
    private static final String EVENT_ANSWER_CALL = "/api/answerCall";
    private static final String EVENT_RECEIVE_ANSWER_CALL = "/api/receiveAnswerCall";
    private static final String EVENT_RECEIVE_CALL = "/api/receiveCall";
    private static final String EVENT_SEND_NEW_ICE = "/api/newIce";
    private static final String EVENT_RECEIVE_NEW_ICE = "/api/receiveIce";

    private String mMyId;
    private String mCurrPeerId;
    private Socket mSocket;

    private EglBase mEglBase;
    private WebRtcPeerConnection mPeerConnection;
    private Camera mCamera;

    private SurfaceViewRenderer mLocalRenderer;
    private SurfaceViewRenderer mRemoteRenderer;
    private TextView mTextMyId;
    private TextView mTextStatus;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        initViews();
        initSocket();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSocket != null) {
            mSocket.disconnect();
        }
    }

    private void initViews() {
        mEglBase = EglBase.create();

        mTextMyId = findViewById(R.id.text_my_id);
        mTextStatus = findViewById(R.id.text_status);
        mLocalRenderer = findViewById(R.id.local_renderer);
        mRemoteRenderer = findViewById(R.id.remote_renderer);

        mLocalRenderer.init(mEglBase.getEglBaseContext(), null);
        mRemoteRenderer.init(mEglBase.getEglBaseContext(), null);
    }

    private void initSocket() {
        try {
            mSocket = IO.socket(WEB_SOCKET_URL);
            mSocket.on(Socket.EVENT_CONNECT, this::onWsConnect);
            mSocket.on(Socket.EVENT_DISCONNECT, this::onWsDisconnect);
            mSocket.on(Socket.EVENT_CONNECT_ERROR, this::onWsConnectError);
            mSocket.on(Socket.EVENT_ERROR, this::onWsError);

            mSocket.on(EVENT_RECEIVE_CALL, this::onWsReceiveCall);
            mSocket.on(EVENT_RECEIVE_NEW_ICE, this::onWsReceiveNewIce);
            mSocket.on(EVENT_RECEIVE_ANSWER_CALL, this::onWsReceiveAnswerCall);

            mTextStatus.setText("connecting...");
            mSocket.connect();
        } catch (URISyntaxException e) {
            Log.d(TAG, "Error initializing web socket: " + e);
        }
    }

    private void initWebRtc() {
        mCamera = new Camera(this, Camera.Facing.Front, null);

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:35.187.238.244:3478"));

        WebRtcPeerConnection.initialize(getApplicationContext());
        PeerConnectionFactory factory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(mEglBase.getEglBaseContext()))
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(mEglBase.getEglBaseContext(), true, true))
                .createPeerConnectionFactory();
        mPeerConnection = new WebRtcPeerConnection(this, mEglBase, mCamera, factory, new PeerConnectionObserver(), iceServers);

        mPeerConnection.enableVideo(true);
        mPeerConnection.enableAudio(true);

        mPeerConnection.getLocalVideoTrack().addSink(mLocalRenderer);
    }

    private void onWsReceiveAnswerCall(Object... args) {
        Log.d(TAG, "receive answer call");

        JSONObject message = (JSONObject) args[0];
        try {
            String sdp = message.getString("sdp");

            mPeerConnection.setRemoteDescriptionSync(new SessionDescription(SessionDescription.Type.ANSWER, sdp));
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (WebRtcPeerConnection.WebRtcPeerConnectionError e) {
            e.printStackTrace();
        }
    }

    private void onWsReceiveNewIce(Object... args) {

        try {
            JSONObject message = (JSONObject) args[0];
            String sdpMid = message.getString("sdpMid");
            int sdpMLineIndex = message.getInt("sdpMLineIndex");
            String sdp = message.getString("sdp");

            IceCandidate ic = new IceCandidate(
                    sdpMid,
                    sdpMLineIndex,
                    sdp
            );
            Log.d(TAG, "receive new ice candidate message: " + ic);
            mPeerConnection.addIceCandidate(ic);
        } catch (JSONException e) {
            Log.d(TAG, "Error while extracting new ice candidate message: " + e);
        }
    }

    private synchronized void onWsReceiveCall(Object... args) {
        Log.d(TAG, "Receive new call");

        try {
            JSONObject message = (JSONObject) args[0];
            String senderId = message.getString("from_id");
            String sdp = message.getString("sdp");
            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdp);

            initWebRtc();
            mPeerConnection.setRemoteDescriptionSync(remoteSdp);

            // answer
            SessionDescription localSdp = mPeerConnection.createAnswerSync(new MediaConstraints());
            mPeerConnection.setLocalDescriptionSync(localSdp);

            mCurrPeerId = senderId;
            sendAnswer(senderId, localSdp);
        } catch (JSONException e) {
            Log.d(TAG, "Error while extracting offer message: " + e);
        } catch (WebRtcPeerConnection.WebRtcPeerConnectionError e) {
            Log.d(TAG, "Error creating answer sdp: " + e);
        }
    }

    private void onWsError(Object... args) {
        Log.d(TAG, "Socket error: " + args);
        runOnUiThread(() -> mTextStatus.setText("error"));
    }

    private void onWsConnectError(Object... args) {
        Log.d(TAG, "Socket connect error: " + args);
        runOnUiThread(() -> mTextStatus.setText("connect error"));
    }

    private void onWsDisconnect(Object... args) {
        Log.d(TAG, "Socket disconnect: " + args);
        runOnUiThread(() -> mTextStatus.setText("disconnect"));
    }

    private void onWsConnect(Object... args) {
        runOnUiThread(() -> mTextStatus.setText("connected"));

        createMyId();
        if (isMakingCall()) {
            mCurrPeerId = getIntent().getStringExtra(EXTRA_ROOM_NAME);
            makeCall(mCurrPeerId);
        } else {
            initWebRtc();
        }
    }

    private void createMyId() {
        runOnUiThread(() -> mTextMyId.setText("creating my id..."));

        mMyId = String.valueOf(new Random(System.currentTimeMillis()).nextInt(Short.MAX_VALUE));

        try {
            JSONObject message = new JSONObject();
            message.put("id", mMyId);
            mSocket.emit(EVENT_CREATE_ID, message, (Ack) args1 -> {
                runOnUiThread(() -> mTextMyId.setText("Id: " + mMyId));
                Log.d(TAG, "create new user id successful: " + mMyId);
            });
        } catch (JSONException e) {
            runOnUiThread(() -> mTextMyId.setText("Create Id Failed"));
            Log.d(TAG, "Failed to create JSON message: " + e);
        }
    }

    private void makeCall(String userId) {
        initWebRtc();

        //
        try {
            SessionDescription mySdp = mPeerConnection.createOfferSync(new MediaConstraints());
            mPeerConnection.setLocalDescriptionSync(mySdp);
            sendOffer(userId, mySdp);
        } catch (WebRtcPeerConnection.WebRtcPeerConnectionError e) {
            Log.d(TAG, "Error while creating/setting local sdp: " + e);
        }
    }

    private void sendOffer(String userId, SessionDescription sdp) {
        try {
            JSONObject message = new JSONObject();
            message.put("from_id", mMyId);
            message.put("to_id", userId);
            message.put("sdp", sdp.description);

            mSocket.emit(EVENT_OFFER_CALL, message, (Ack) args -> Log.d(TAG, "Call offer sent"));
        } catch (JSONException e) {
            Log.d(TAG, "Error while creating offer message: " + e);
        }
    }

    private void sendAnswer(String userId, SessionDescription sdp) {
        try {
            JSONObject message = new JSONObject();
            message.put("from_id", mMyId);
            message.put("to_id", userId);
            message.put("sdp", sdp.description);

            mSocket.emit(EVENT_ANSWER_CALL, message, (Ack) args -> {
                Log.d(TAG, "Call answer sent");
            });
        } catch (JSONException e) {
            Log.d(TAG, "Error while creating answer message: " + e);
        }
    }

    private void sendIceCandidate(String userId, IceCandidate iceCandidate) {
        try {
            JSONObject message = new JSONObject();
            message.put("to_id", userId);
            message.put("sdpMid", iceCandidate.sdpMid);
            message.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            message.put("sdp", iceCandidate.sdp);

            mSocket.emit(EVENT_SEND_NEW_ICE, message, (Ack) args -> Log.d(TAG, "New ice candidate sent to " + userId));
        } catch (JSONException e) {
            Log.d(TAG, "Error constructing new ice candidate message: " + e);
        }
    }

    private boolean isMakingCall() {
        return getIntent().getBooleanExtra(EXTRA_IS_MAKING_CALL, false);
    }

    public void onSwitchCamClick(View view) {
        if (mCamera != null) {
            mCamera.switchCamera();
        }
    }

    private class PeerConnectionObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.d(TAG, "onIceConnectionChange: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.d(TAG, "onIceConnectionReceivingChange: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.d(TAG, "onIceCandidate: " + iceCandidate);

            if (mCurrPeerId != null) {
                Log.d(TAG, "Sending new ice candidate to " + mCurrPeerId);
                sendIceCandidate(mCurrPeerId, iceCandidate);
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "onIceCandidatesRemoved: " + iceCandidates);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream: " + mediaStream);
            for (AudioTrack at : mediaStream.audioTracks) {
                at.setEnabled(true);
            }

            if (mediaStream.videoTracks != null && mediaStream.audioTracks.size() == 1) {
                VideoTrack vt = mediaStream.videoTracks.get(0);
                vt.setEnabled(true);
                vt.addSink(mRemoteRenderer);
            }
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream: " + mediaStream);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel: " + dataChannel);
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack");
        }
    }
}
