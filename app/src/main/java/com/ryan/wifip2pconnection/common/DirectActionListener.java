package com.ryan.wifip2pconnection.common;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;

import java.util.Collection;

public interface DirectActionListener extends WifiP2pManager.ChannelListener {
    // P2P是否可用
    void wifiP2pEnabled(boolean enabled);
    // 已经连接，得到P2P设备信息
    void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo);
    // 连接断开
    void onDisconnection();
    //
    void onSelfDeviceAvailable(WifiP2pDevice wifiP2pDevice);
    // 有搜索到设备
    void onPeersAvailable(Collection<WifiP2pDevice> wifiP2pDeviceList);
}
