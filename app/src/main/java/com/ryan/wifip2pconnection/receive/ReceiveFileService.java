package com.ryan.wifip2pconnection.receive;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.ryan.wifip2pconnection.common.Constants;
import com.ryan.wifip2pconnection.common.FileTransfer;
import com.ryan.wifip2pconnection.utils.Logger;
import com.ryan.wifip2pconnection.utils.Md5Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ReceiveFileService extends IntentService {

    public ReceiveFileService() {
        super("ReceiveFileService");
    }

    private ServerSocket serverSocket;

    private InputStream inputStream;

    private ObjectInputStream objectInputStream;

    private FileOutputStream fileOutputStream;

    private OnProgressChangListener progressChangListener;

    /**
     * 文件传输的回调
     */
    public interface OnProgressChangListener {
        //当传输进度发生变化时
        void onProgressChanged(FileTransfer fileTransfer, int progress);
        //当传输结束时
        void onTransferFinished(File file);

    }

    public class MyBinder extends Binder {
        public ReceiveFileService getService() {
            return ReceiveFileService.this;
        }
    }



    @Override
    public void onCreate() {
        Logger.d("ReceiveFileService onCreate");
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Logger.d("ReceiveFileService onBind");
        return new MyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Logger.d("ReceiveFileService onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d("ReceiveFileService onDestroy");
        clean();
    }

    public void setProgressChangListener(OnProgressChangListener progressChangListener) {
        this.progressChangListener = progressChangListener;
    }

    private void clean() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (inputStream != null) {
            try {
                inputStream.close();
                inputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (objectInputStream != null) {
            try {
                objectInputStream.close();
                objectInputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
                fileOutputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // 使用 IntentService 在后台监听客户端的 Socket 连接请求，并通过输入输出流来传输文件。
    // 此处的代码比较简单，就只是在指定端口一直堵塞监听客户端的连接请求，获取待传输的文件信息模型 FileTransfer ，之后就进行实际的数据传输
    // 注意这里有一个小技巧，service只被bind了一次，但是可以调用多次startService,这里的onHandleIntent也会被多次调用
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Logger.d("ReceiveFileService onHandleIntent+++++");
        clean();
        File file = null;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(Constants.PORT));
            Logger.w("服务端监听socket监听中......");
            Socket client = serverSocket.accept();
            Logger.w("有客户端连接上了, 客户端IP地址 : " + client.getInetAddress().getHostAddress());

            inputStream = client.getInputStream();
            objectInputStream = new ObjectInputStream(inputStream);
            FileTransfer fileTransfer = (FileTransfer) objectInputStream.readObject(); // 注意这里，直接readObject
            Logger.w("待接收的文件: " + fileTransfer);

            String name = new File(fileTransfer.getFilePath()).getName();
            //将文件存储至指定位置
            file = new File(Environment.getExternalStorageDirectory() + "/" + name);
            fileOutputStream = new FileOutputStream(file);
            byte[] buf = new byte[512];
            int len;
            long total = 0;
            int progress;
            while ((len = inputStream.read(buf)) != -1) {
                fileOutputStream.write(buf, 0, len);
                total += len;
                progress = (int) ((total * 100) / fileTransfer.getFileLength());
                Logger.w( "文件接收进度: " + progress);
                if (progressChangListener != null) {
                    progressChangListener.onProgressChanged(fileTransfer, progress);
                }
            }
            serverSocket.close();
            inputStream.close();
            objectInputStream.close();
            fileOutputStream.close();
            serverSocket = null;
            inputStream = null;
            objectInputStream = null;
            fileOutputStream = null;
            Logger.w( "文件接收成功，文件的MD5码是：" + Md5Util.getMd5(file));
        }
        catch (Exception e) {
            Logger.e( "文件接收 Exception: " + e.getMessage());
        }
        finally {
            clean();
            if (progressChangListener != null) {
                progressChangListener.onTransferFinished(file);
            }
            //再次启动服务，等待客户端下次连接
            startService(new Intent(this, ReceiveFileService.class));
        }
    }


}
