package com.ryan.wifip2pconnection.common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;

import com.ryan.wifip2pconnection.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class DirectBroadcastReceiver extends BroadcastReceiver {

    /**
     * 监听4个广播
     */
    public static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        return intentFilter;
    }

    private WifiP2pManager mWifiP2pManager;

    private WifiP2pManager.Channel mChannel;

    private DirectActionListener mDirectActionListener;

    public DirectBroadcastReceiver(WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, DirectActionListener directActionListener) {
        mWifiP2pManager = wifiP2pManager;
        mChannel = channel;
        mDirectActionListener = directActionListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                // 用于指示 Wifi P2P 是否可用
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -100);
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        mDirectActionListener.wifiP2pEnabled(true);
                        Logger.d("DirectBroadcastReceiver: WIFI_P2P_STATE_CHANGED_ACTION 可用Enable");
                    } else {
                        mDirectActionListener.wifiP2pEnabled(false);
                        Logger.d("DirectBroadcastReceiver: WIFI_P2P_STATE_CHANGED_ACTION 不可用Disable");
                        List<WifiP2pDevice> wifiP2pDeviceList = new ArrayList<>();
                        mDirectActionListener.onPeersAvailable(wifiP2pDeviceList);
                    }
                    break;

                // Wifi P2P 的连接状态发生了改变WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (networkInfo != null && networkInfo.isConnected()) {
                        // 在此处可以通过 requestConnectionInfo 获取到组连接信息，信息最后通过 onConnectionInfoAvailable 方法传递出来，在此可以判断当前设备是否为群主，获取群组IP地址
                        mWifiP2pManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                mDirectActionListener.onConnectionInfoAvailable(info);
                            }
                        });
                        Logger.d("DirectBroadcastReceiver: WIFI_P2P_CONNECTION_CHANGED_ACTION 已连接p2p设备");
                    } else {
                        mDirectActionListener.onDisconnection();
                        Logger.d("DirectBroadcastReceiver: WIFI_P2P_CONNECTION_CHANGED_ACTION 与p2p设备已断开连接");
                    }
                    break;

                // 搜索结束后，系统就会触发 WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION 广播，表示对等节点列表发生了变化
                // 可以通过 requestPeers 方法得到可用的设备列表，之后就可以选择当中的某一个设备进行连接操作
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    Logger.d("DirectBroadcastReceiver: WIFI_P2P_PEERS_CHANGED_ACTION");
                    // requestPeers 方法获取设备列表信息，此处用 RecyclerView 展示列表，在 onPeersAvailable 方法刷新列表
                    mWifiP2pManager.requestPeers(mChannel, new WifiP2pManager.PeerListListener() {
                        @Override
                        public void onPeersAvailable(WifiP2pDeviceList peers) {
                            Logger.d("DirectBroadcastReceiver: WIFI_P2P_PEERS_CHANGED_ACTION onPeersAvailable 获取设备列表");
                            mDirectActionListener.onPeersAvailable(peers.getDeviceList());
                        }
                    });
                    break;

                // 本设备的设备信息发生了变化---可以获取到本机的信息
                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                    Logger.d("DirectBroadcastReceiver: WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
                    WifiP2pDevice wifiP2pDevice = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                    mDirectActionListener.onSelfDeviceAvailable(wifiP2pDevice);
                    break;
            }
        }
    }
}
