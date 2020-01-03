package com.ryan.wifip2pconnection.receive;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.ryan.wifip2pconnection.R;
import com.ryan.wifip2pconnection.common.BaseActivity;
import com.ryan.wifip2pconnection.common.DirectActionListener;
import com.ryan.wifip2pconnection.common.DirectBroadcastReceiver;
import com.ryan.wifip2pconnection.common.FileTransfer;
import com.ryan.wifip2pconnection.utils.Logger;

import java.io.File;
import java.util.Collection;

/**
 * 用于接收文件的服务端
 */
public class ReceiveFileActivity extends BaseActivity {

    private ProgressDialog progressDialog;
    private ImageView iv_image;
    private TextView tv_log;

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    // 是否可以连接
    private boolean connectionInfoAvailable = false;

    // 用来接收WIFI P2P的各种广播
    private DirectBroadcastReceiver broadcastReceiver;
    // 用来接收文件的服务
    private ReceiveFileService receiveFileService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_file);
        initView();
        initData();
        createGroup();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiveFileService != null) {
            receiveFileService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        stopService(new Intent(this, ReceiveFileService.class));
        unregisterReceiver(broadcastReceiver);
        if (connectionInfoAvailable) {
            removeGroup();
        }
    }

    private void initView() {
        setTitle("接收文件");
        iv_image = findViewById(R.id.iv_image);
        tv_log = findViewById(R.id.tv_log);
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在接收文件");
        progressDialog.setMax(100);
    }

    private void initData() {
        wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            showToast("获取WIFI P2P Manager失败，退出");
            finish();
            return;
        }

        // 创建channel
        channel = wifiP2pManager.initialize(this, this.getMainLooper(), directActionListener);
        broadcastReceiver = new DirectBroadcastReceiver(wifiP2pManager, channel, directActionListener);
        // 注册P2P的各种广播
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter());
        // 绑定接收服务
        bindService();
    }

    /**
     * 主动创建群组
     * 此处为了简化操作，直接指定某台设备作为服务器端（群主），即直接指定某台设备用来接收文件
     * 因此，服务器端要主动创建群组，并等待客户端的连接
     */
    private void createGroup() {
        wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                printlog("createGroup 成功");
                dismissLoadingDialog();
                showToast("createGroup 成功");
            }

            @Override
            public void onFailure(int reason) {
                printlog("createGroup 失败: " + reason);
                dismissLoadingDialog();
                showToast("createGroup 失败");
            }
        });
    }

    /**
     * 离开群组
     */
    private void removeGroup() {
        wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                printlog("removeGroup 成功");
                showToast("removeGroup 成功");
            }

            @Override
            public void onFailure(int reason) {
                printlog("removeGroup 失败");
                showToast("removeGroup 失败");
            }
        });
    }


    private DirectActionListener directActionListener = new DirectActionListener() {

        @Override
        public void wifiP2pEnabled(boolean enabled) {
            Logger.d("Receive回调: wifiP2pEnabled enabled="+enabled);
            printlog("wifiP2pEnabled 设备可用="+enabled);
        }

        @Override
        public void onChannelDisconnected() {
            Logger.d("Receive回调: onChannelDisconnected");
            printlog("onChannelDisconnected Channel断开了");
        }

        // 注意，如果createGroup时，也会回调到这个onConnectionInfoAvailable
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            Logger.d("Receive回调: onConnectionInfoAvailable wifiP2pInfo isGroupOwner="+wifiP2pInfo.isGroupOwner+", groupFormed="+wifiP2pInfo.groupFormed);
            printlog("onConnectionInfoAvailable 获取到连接上的设备信息 wifiP2pInfo isGroupOwner="+wifiP2pInfo.isGroupOwner+", groupFormed="+wifiP2pInfo.groupFormed);
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionInfoAvailable = true;
                if (receiveFileService != null) {
                    startService(ReceiveFileService.class);
                }
            }
        }

        @Override
        public void onDisconnection() {
            Logger.d("Receive回调: onDisconnection");
            printlog("onDisconnection断开连接");
            connectionInfoAvailable = false;
        }

        @Override
        public void onSelfDeviceAvailable(WifiP2pDevice wifiP2pDevice) {
            Logger.d("Receive回调: onSelfDeviceAvailable 获取到本机设备信息 wifiP2pDevice="+wifiP2pDevice);
            printlog("onSelfDeviceAvailable获取到本机设备信息 wifiP2pDevice="+wifiP2pDevice);
        }

        @Override
        public void onPeersAvailable(Collection<WifiP2pDevice> wifiP2pDeviceList) {
            Logger.d("Receive回调: onPeersAvailable wifiP2pDeviceList.size="+ (wifiP2pDeviceList == null ? 0 : wifiP2pDeviceList.size()));
            if (wifiP2pDeviceList != null) {
                for (WifiP2pDevice wifiP2pDevice : wifiP2pDeviceList) {
                    Logger.d("Receive回调: onPeersAvailable 获取到远端设备信息 "+ wifiP2pDevice.toString());
                    printlog("onPeersAvailable获取到远端设备信息 "+ wifiP2pDevice.toString());
                }
            }
        }
    };

    private void printlog(String log) {
        tv_log.append(log + "\n");
        tv_log.append("----------" + "\n");
    }


    private void bindService() {
        Intent intent = new Intent(this, ReceiveFileService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printlog("onServiceConnected 绑定 ReceiveFileService 服务成功");
            ReceiveFileService.MyBinder binder = (ReceiveFileService.MyBinder) service;
            receiveFileService = binder.getService();
            receiveFileService.setProgressChangListener(progressChangListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            receiveFileService = null;
            bindService(); // 断开连接后，重新绑定
        }
    };


    private ReceiveFileService.OnProgressChangListener progressChangListener = new ReceiveFileService.OnProgressChangListener() {
        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final int progress) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setMessage("文件名： " + new File(fileTransfer.getFilePath()).getName());
                    progressDialog.setProgress(progress);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferFinished(final File file) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.cancel();
                    if (file != null && file.exists()) {
                        Glide.with(ReceiveFileActivity.this).load(file.getPath()).into(iv_image);
                    }
                }
            });
        }
    };
}
