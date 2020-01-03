package com.ryan.wifip2pconnection.send;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ryan.wifip2pconnection.BuildConfig;
import com.ryan.wifip2pconnection.MainActivity;
import com.ryan.wifip2pconnection.R;
import com.ryan.wifip2pconnection.common.BaseActivity;
import com.ryan.wifip2pconnection.common.Constants;
import com.ryan.wifip2pconnection.common.DirectActionListener;
import com.ryan.wifip2pconnection.common.DirectBroadcastReceiver;
import com.ryan.wifip2pconnection.common.FileTransfer;
import com.ryan.wifip2pconnection.utils.Glide4Engine;
import com.ryan.wifip2pconnection.utils.Logger;
import com.ryan.wifip2pconnection.widget.DeviceAdapter;
import com.ryan.wifip2pconnection.widget.LoadingDialog;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.internal.entity.CaptureStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 客户端（用来发送文件）主动搜索附近的设备，加入到服务器创建的群组，获取服务器的 IP 地址，向其发起文件传输请求
 */
public class SendFileActivity extends BaseActivity {

    private static final int CODE_CHOOSE_FILE = 100;

    private TextView tv_myDeviceName;
    private TextView tv_myDeviceAddress;
    private TextView tv_myDeviceStatus;
    private TextView tv_status;
    private List<WifiP2pDevice> wifiP2pDeviceList;
    private DeviceAdapter deviceAdapter;
    private Button btn_disconnect;
    private Button btn_chooseFile;

    private LoadingDialog loadingDialog;

    private WifiP2pManager wifiP2pManager;

    private WifiP2pManager.Channel channel;

    private WifiP2pInfo wifiP2pInfo;

    private WifiP2pDevice mWifiP2pDevice;

    private boolean wifiP2pEnabled = false;

    private DirectBroadcastReceiver broadcastReceiver;


    private DirectActionListener directActionListener = new DirectActionListener() {

        @Override
        public void wifiP2pEnabled(boolean enabled) {
            wifiP2pEnabled = enabled;
        }

        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            dismissLoadingDialog();
            wifiP2pDeviceList.clear();
            deviceAdapter.notifyDataSetChanged();
            btn_disconnect.setEnabled(true);
            btn_chooseFile.setEnabled(true);
            Logger.d("onConnectionInfoAvailable");
            Logger.d("onConnectionInfoAvailable groupFormed: " + wifiP2pInfo.groupFormed);
            Logger.d("onConnectionInfoAvailable isGroupOwner: " + wifiP2pInfo.isGroupOwner);
            Logger.d("onConnectionInfoAvailable getHostAddress: " + wifiP2pInfo.groupOwnerAddress.getHostAddress());
            StringBuilder stringBuilder = new StringBuilder();
            if (mWifiP2pDevice != null) {
                stringBuilder.append("连接的设备名：");
                stringBuilder.append(mWifiP2pDevice.deviceName);
                stringBuilder.append("\n");
                stringBuilder.append("连接的设备的地址：");
                stringBuilder.append(mWifiP2pDevice.deviceAddress);
            }
            stringBuilder.append("\n");
            stringBuilder.append("是否群主：");
            stringBuilder.append(wifiP2pInfo.isGroupOwner ? "是群主" : "非群主");
            stringBuilder.append("\n");
            stringBuilder.append("群主IP地址：");
            stringBuilder.append(wifiP2pInfo.groupOwnerAddress.getHostAddress());
            tv_status.setText(stringBuilder);
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                SendFileActivity.this.wifiP2pInfo = wifiP2pInfo;
            }
        }

        @Override
        public void onDisconnection() {
            Logger.d("onDisconnection");
            btn_disconnect.setEnabled(false);
            btn_chooseFile.setEnabled(false);
            showToast("处于非连接状态");
            wifiP2pDeviceList.clear();
            deviceAdapter.notifyDataSetChanged();
            tv_status.setText(null);
            SendFileActivity.this.wifiP2pInfo = null;
        }

        @Override
        public void onSelfDeviceAvailable(WifiP2pDevice wifiP2pDevice) {
            Logger.d("onSelfDeviceAvailable");
            Logger.d("DeviceName: " + wifiP2pDevice.deviceName);
            Logger.d("DeviceAddress: " + wifiP2pDevice.deviceAddress);
            Logger.d("Status: " + wifiP2pDevice.status);
            tv_myDeviceName.setText(wifiP2pDevice.deviceName);
            tv_myDeviceAddress.setText(wifiP2pDevice.deviceAddress);
            tv_myDeviceStatus.setText(Constants.getDeviceStatus(wifiP2pDevice.status));
        }

        @Override
        public void onPeersAvailable(Collection<WifiP2pDevice> wifiP2pDeviceList) {
            Logger.d("onPeersAvailable :" + wifiP2pDeviceList.size());
            SendFileActivity.this.wifiP2pDeviceList.clear();
            SendFileActivity.this.wifiP2pDeviceList.addAll(wifiP2pDeviceList);
            deviceAdapter.notifyDataSetChanged();
            loadingDialog.cancel();
        }

        @Override
        public void onChannelDisconnected() {
            Logger.d("onChannelDisconnected");
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_file);
        initView();
        initEvent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    private void initEvent() {
        wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            finish();
            return;
        }
        channel = wifiP2pManager.initialize(this, getMainLooper(), directActionListener);
        broadcastReceiver = new DirectBroadcastReceiver(wifiP2pManager, channel, directActionListener);
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter());
    }

    private void initView() {
        setTitle("发送文件");
        tv_myDeviceName = findViewById(R.id.tv_myDeviceName);
        tv_myDeviceAddress = findViewById(R.id.tv_myDeviceAddress);
        tv_myDeviceStatus = findViewById(R.id.tv_myDeviceStatus);
        tv_status = findViewById(R.id.tv_status);
        btn_disconnect = findViewById(R.id.btn_disconnect);
        btn_chooseFile = findViewById(R.id.btn_chooseFile);
        btn_disconnect.setOnClickListener(clickListener);
        btn_chooseFile.setOnClickListener(clickListener);
        loadingDialog = new LoadingDialog(this);
        RecyclerView rv_deviceList = findViewById(R.id.rv_deviceList);
        wifiP2pDeviceList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(wifiP2pDeviceList);
        deviceAdapter.setClickListener(new DeviceAdapter.OnClickListener() {
            @Override
            public void onItemClick(int position) {
                mWifiP2pDevice = wifiP2pDeviceList.get(position);
                showToast(mWifiP2pDevice.deviceName);
                connect();
            }
        });
        rv_deviceList.setAdapter(deviceAdapter);
        rv_deviceList.setLayoutManager(new LinearLayoutManager(this));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE_CHOOSE_FILE && resultCode == RESULT_OK) {
            List<String> strings = Matisse.obtainPathResult(data);
            if (strings != null && !strings.isEmpty()) {
                String path = strings.get(0);
                Logger.d("文件路径：" + path);
                File file = new File(path);
                if (file.exists() && wifiP2pInfo != null) {
                    FileTransfer fileTransfer = new FileTransfer(file.getPath(), file.length());
                    new SendFileTask(this, fileTransfer).execute(wifiP2pInfo.groupOwnerAddress.getHostAddress());
                }
            }
        }
    }

    // 之后，通过点击事件选中群主（服务器端）设备，通过 connect 方法请求与之进行连接
    // 此处依然无法通过函数函数来判断连接结果，需要依靠系统发出的 WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION 方法来获取到连接结果，
    private void connect() {
        WifiP2pConfig config = new WifiP2pConfig();
        if (config.deviceAddress != null && mWifiP2pDevice != null) {
            config.deviceAddress = mWifiP2pDevice.deviceAddress;
            config.wps.setup = WpsInfo.PBC;
            showLoadingDialog("正在连接 " + mWifiP2pDevice.deviceName);
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Logger.d("connect onSuccess");
                }

                @Override
                public void onFailure(int reason) {
                    showToast("连接失败 " + reason);
                    dismissLoadingDialog();
                }
            });
        }
    }

    private void disconnect() {
        wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Logger.d("disconnect onFailure:" + reasonCode);
            }

            @Override
            public void onSuccess() {
                Logger.d("disconnect onSuccess");
                tv_status.setText(null);
                btn_disconnect.setEnabled(false);
                btn_chooseFile.setEnabled(false);
            }
        });
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuDirectDiscover: {
                if (!wifiP2pEnabled) {
                    showToast("需要先打开Wifi");
                    return true;
                }
                loadingDialog.show("正在搜索附近设备", true, false);
                wifiP2pDeviceList.clear();
                deviceAdapter.notifyDataSetChanged();
                //搜寻附近带有 Wi-Fi P2P 的设备
                wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        showToast("Success");
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        showToast("Failure");
                        loadingDialog.cancel();
                    }
                });
                return true;
            }
            default:
                return true;
        }
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_disconnect: {
                    disconnect();
                    break;
                }
                case R.id.btn_chooseFile: {
                    navToChose();
                    break;
                }
            }
        }
    };


    private void navToChose() {
        Matisse.from(this)
                .choose(MimeType.ofImage())
                .countable(true)
                .showSingleMediaType(true)
                .maxSelectable(1)
                .capture(false)
                .captureStrategy(new CaptureStrategy(true, BuildConfig.APPLICATION_ID + ".fileprovider"))
                .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                .thumbnailScale(0.70f)
                .imageEngine(new Glide4Engine())
                .forResult(CODE_CHOOSE_FILE);
    }

}
