package com.zzw.multidownload;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private EditText et_path;
    private ProgressBar pb;
    private TextView tv;
    public static int threadCount = 3;
    public static int runningThread = 3;
    public int currentProgress = 0;
    protected static final int DOWN_LOAD_FALSE = 1;
    protected static final int SERVER_FALSE = 2;
    protected static final int DOWN_LOAD_SUCCESS = 3;
    protected static final int UPDATE_TEXT = 4;

    private Handler handle = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case DOWN_LOAD_FALSE:
                    Toast.makeText(getApplicationContext(),"下载失败",Toast.LENGTH_SHORT).show();
                    break;
                case SERVER_FALSE:
                    Toast.makeText(getApplicationContext(),"服务器异常，下载失败",Toast.LENGTH_SHORT).show();
                    break;
                case DOWN_LOAD_SUCCESS:
                    Toast.makeText(getApplicationContext(),"文件下载完毕",Toast.LENGTH_SHORT).show();
                    break;
                case UPDATE_TEXT:
                    tv.setText("当前进度：" + pb.getProgress()*100/pb.getMax() + "%");
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        et_path = (EditText)this.findViewById(R.id.et_path);
        pb = (ProgressBar)this.findViewById(R.id.pb);
        tv = (TextView)this.findViewById(R.id.tv);
    }

    public void download(View view) {
        final String path = et_path.getText().toString().trim();
        if(TextUtils.isEmpty(path)){
            Toast.makeText(this, "下载路径不能为空！", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(){
            @Override
            public void run() {
                // String path = "http://192.168.1.100:8080/360.exe";
                try {
                    URL url = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setReadTimeout(5000);
                    int code = conn.getResponseCode();
                    if(code == 200){
                        int length = conn.getContentLength();
                        pb.setProgress(length);
                        // 在客户端本地创建一个大小跟服务器端文件大小一样的临时文件
                        RandomAccessFile raf = new RandomAccessFile("/sdcard/setup.exe","rwd");
                        raf.setLength(length);
                        raf.close();

                        // 假设是3个线程下载文件
                        // 平均每个线程下载的文件大小
                        int blockSize = length / threadCount;
                        for (int threadId =1;threadId<=threadCount;threadId++) {
                            int startIndex = (threadId - 1) * blockSize;
                            int endIndex = (threadId * blockSize) - 1;
                            if (threadId == threadCount) {
                                endIndex = length;
                            }
                            Log.i("zzw","线程：" + threadId + "下载：-----" +
                                    startIndex + "--->" + endIndex);
                            new DownLoadThread(path,threadId,startIndex,endIndex).start();
                        }
                    }else{
                        Message msg = new Message();
                        msg.what =SERVER_FALSE;
                        handle.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = new Message();
                    msg.what =DOWN_LOAD_FALSE;
                    handle.sendMessage(msg);
                }
            }
        }.start();
    }

    public class DownLoadThread extends Thread {
        private int threadId;
        private int startIndex;
        private int endIndex;
        private String path;

        public DownLoadThread(String path,int threadId, int startIndex,int endIndex){
            this.path = path;
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public void run() {
            try {
              /* ----检查是否存在记录已经下载长度的文件，如果存在读取文件的内容-----*/
                File file = new File("/sdcard/" + threadId +".txt");
                if (file.exists() && file.length() > 0) {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] buffer = new byte[1024];
                    int len = fis.read(buffer);
                    String downLoadLen = new String(buffer,0,len);
                    int downLoadLenInt = Integer.parseInt(downLoadLen);

                    // 设置断点下载时进度条起始位置
                    int alreadyDownload = downLoadLenInt - startIndex; // 已经下载的文件
                    currentProgress += alreadyDownload;

                    startIndex = downLoadLenInt;
                    fis.close();
                }
                /*---------------------------------------------------------------------*/
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("GET");
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Range", "bytes=" + startIndex +
                        "-" + endIndex);
                Log.i("zzw","线程真实下载：" + threadId +"下载---"+ startIndex + "--->" + endIndex );
                int code = conn.getResponseCode();
                if (code == 206) {
                    InputStream is = conn.getInputStream();
                    RandomAccessFile raf = new RandomAccessFile("/sdcard/setup.exe","rwd");
                    raf.seek(startIndex);
                    int len;
                    byte[] temp = new byte[1024];
                    int total = 0;
                    while((len = is.read(temp)) != -1){
                        // 记录当前线程下载的数据长度
                        RandomAccessFile ra = new RandomAccessFile("/sdcard/setup.exe" +threadId+".txt","rwd");
                        ra.write(temp,0,len);
                        total+=len;
                        ra.write((total + startIndex + "").getBytes());
                        ra.close();

                        synchronized (MainActivity.this) {
                            // 获取所有线程下载的总进度
                            currentProgress += len;
                            // 更新进度条
                            pb.setProgress(currentProgress);
                            // ProgressBar、ProgressDialog 可以直接更新进度条。

                            Message msg = Message.obtain();// Message库中获取一个已经存在的Message对象
                            msg.what = UPDATE_TEXT;
                            handle.sendMessage(msg);
                        }
                    }
                    is.close();
                    raf.close();
                    Log.i("zzw","线程：" + threadId + "下载完毕了。。。");
                }else{
                    Log.i("zzw","线程：" + threadId + "下载失败了。。。");
                }
            }catch (Exception e) {
                e.printStackTrace();
            }finally {
                // 各个线程下载完毕后删除记录各个线程下载进度的文件
                /*synchronized (MainActivity.this) {
                    runningThread--;
                    if (runningThread == 0) {
                        for (int i = 0; i <= 3; i++) {
                            File file = new File("/sdcard/" + i + ".txt");
                            file.delete();
                        }
                        Message msg = new Message();
                        msg.what = DOWN_LOAD_SUCCESS;
                        handle.sendMessage(msg);
                    }
                }*/
                ThreadFinish();
            }
        }

        public synchronized void ThreadFinish(){
            runningThread--;
            if (runningThread == 0) {
                for (int i = 0; i <= 3; i++) {
                    File file = new File("/sdcard/" + i + ".txt");
                    file.delete();
                }
                Message msg = new Message();
                msg.what = DOWN_LOAD_SUCCESS;
                handle.sendMessage(msg);
            }
        }
    }
}
