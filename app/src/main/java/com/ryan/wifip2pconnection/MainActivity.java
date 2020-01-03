package com.ryan.wifip2pconnection;


import android.os.Bundle;
import android.view.View;

import com.ryan.wifip2pconnection.common.BaseActivity;
import com.ryan.wifip2pconnection.receive.ReceiveFileActivity;
import com.ryan.wifip2pconnection.send.SendFileActivity;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
    }

    /**
     * 启动发送文件的客户端
     * @param view
     */
    public void startFileSenderActivity(View view) {
        startActivity(SendFileActivity.class);
    }

    /**
     * 启动接收文件的服务端
     * @param view
     */
    public void startFileReceiverActivity(View view) {
        startActivity(ReceiveFileActivity.class);
    }
}
