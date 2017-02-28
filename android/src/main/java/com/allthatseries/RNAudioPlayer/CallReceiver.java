package com.allthatseries.RNAudioPlayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CallReceiver extends BroadcastReceiver {
    Boolean isStateIdle = true;
    TelephonyManager telManager;
    Context context;

    private static final String TAG = "CallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        this.context = context;

        telManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        telManager.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private final PhoneStateListener phoneListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
        Log.d(TAG, "onCallStateChanged" + incomingNumber);
        try {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE: {
                    if (isStateIdle) {
                        sendMessage("CALL_STATE_IDLE");
                        isStateIdle = true;
                    }
                    break;
                }
                case TelephonyManager.CALL_STATE_OFFHOOK: {
                    isStateIdle = false;
                    sendMessage("CALL_STATE_OFFHOOK");
                    break;
                }
                case TelephonyManager.CALL_STATE_RINGING: {
                    isStateIdle = false;
                    sendMessage("CALL_STATE_RINGING");
                    break;
                }
                default: {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        }
    };

    private void sendMessage(String strCallState) {
        Intent intent = new Intent("call-state-event");
        // You can also include some extra data.
        intent.putExtra("callState", strCallState);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
