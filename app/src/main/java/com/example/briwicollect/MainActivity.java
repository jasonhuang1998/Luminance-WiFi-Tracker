package com.example.briwicollect;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.FileWriter;
import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.os.Environment;
import android.net.Uri;
import com.opencsv.CSVWriter;
import java.util.Calendar;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.text.DecimalFormat;


import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
    public static final String TAG = MainActivity.class.getSimpleName() + "My";
    WifiManager wifiManager;
    RecyclerViewAdapter mAdapter;
    ConnectivityManager.NetworkCallback mNetwork;
    /**
     * 賦予handler重複執行掃描工作
     */
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            wifiScan();
        }
    };
    /**
     * 建立Runnable, 使掃描可被重複執行
     */
    Runnable searchWifi = new Runnable() {
        @Override
        public void run() {
            handler.sendEmptyMessage(1);
            handler.postDelayed(this, 1000);
        }
    };

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private TextView lightTextView;

    private float lightValue;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /**取得讀寫權限*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }
        getPermission();/**取得定位權限(搜尋Wifi要記得開定位喔)*/

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);/**取得WifiManager*/
        RecyclerView recyclerView = findViewById(R.id.recyclerVIew_SearchResult);/**設置顯示回傳的Recyclerview*/
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.HORIZONTAL));/**為Recyclerview每個項目之間加入分隔線*/
        mAdapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(mAdapter);
        Button btScan = findViewById(R.id.button1);/**按下按鈕則執行掃描*/
        btScan.setOnClickListener(v -> {
            makeCSV();
            recyclerView.setVisibility(View.VISIBLE);
            handler.post(searchWifi);

            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        });
        Button btEnd = findViewById(R.id.button2);/**按下按鈕則結束掃描*/
        btEnd.setOnClickListener(v -> {
            handler.removeCallbacks(searchWifi);
            recyclerView.setVisibility(View.GONE);
        });
        lightTextView = findViewById(R.id.lightTextView);
    }

    private String getFileName(){
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis());
        return date + ".csv";
    };
    private void makeCSV() {
        new Thread(() -> {
            /**決定檔案名稱*/
            String fileName =  getFileName();
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), fileName);
            if(!file.exists()){
//            if(true){
                    //設置第一列的內容
                    String[] title ={"Time","WiFi-Name","RSSI", "Brightness"};
                    StringBuffer csvText = new StringBuffer();
                    for (int i = 0; i < title.length; i++) {
                        csvText.append(title[i]+",");
                    }
                csvText.append("\n");
                    Log.d(TAG, "Create a new CSV file.");//可在此監視輸出的內容
                    runOnUiThread(() -> {
                        try {
                            //->遇上exposed beyond app through ClipData.Item.getUri() 錯誤時在onCreate加上這行
//                            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
//                            StrictMode.setVmPolicy(builder.build());
//                            builder.detectFileUriExposure();
                            //->遇上exposed beyond app through ClipData.Item.getUri() 錯誤時在onCreate加上這行
                            File fileLocation = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), fileName);
                            FileOutputStream fos = new FileOutputStream(fileLocation);
//                            Log.d(TAG, fileLocation.getAbsolutePath());
                            fos.write(csvText.toString().getBytes());
//                            Log.d(TAG, fos.toString());
                            Uri path = Uri.fromFile(fileLocation);
                            Intent fileIntent = new Intent(Intent.ACTION_SEND);
                            fileIntent.setType("text/csv");
                            fileIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                            fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            fileIntent.putExtra(Intent.EXTRA_STREAM, path);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.w(TAG, "makeCSV: "+e.toString());
                        }
                    });
                }
        }).start();
    }//makeCSV
    @Override
    protected void onStop() {
        super.onStop();
        /**跳出畫面則停止掃描*/
        handler.removeCallbacks(searchWifi);
        /**斷開現在正連線著的Wifi(Wifi下篇新增內容)*/
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                /**若為Android10的手機，則在此執行斷線*/
                @SuppressLint("ServiceCast")
                ConnectivityManager connectivityManager = (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
                assert connectivityManager != null;
                connectivityManager.unregisterNetworkCallback(mNetwork);
            } else {
                /**非Android10手機，則執行WifiManager的斷線*/
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration configuration : list){
                    wifiManager.removeNetwork(configuration.networkId);
                }
                wifiManager.disconnect();
//                unregisterReceiver(wifiBroadcastReceiver);
            }
        }catch (Exception e){
            Log.i(TAG, "onStop: "+e.toString());
        }


    }
    /**取得權限*/
    private void getPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100
            );
        }
    }

    private void wifiScan() {
        new Thread(() -> {
            wifiManager.setWifiEnabled(true);/**設置Wifi回傳可被使用*/
            wifiManager.startScan();/**開始掃描*/
            /**取得掃描到的Wifi*/
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {return;}
            List<ScanResult> wifiList = wifiManager.getScanResults();
            for (int i = 0; i <wifiList.size() ; i++) {
                ScanResult s = wifiList.get(i);
                addCSVContent(s.SSID, s.level, lightValue);
            }
            runOnUiThread(() -> {mAdapter.addItem(wifiList);/**更新掃描後的列表*/});
        }).start();
    }
    private void addCSVContent(String wifiname, int  wifirssi, float lightValue){
//        Log.d(TAG,  wifiname+ " : " + wifirssi+"\n");
        String filepath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + getFileName();
        Log.d(TAG,  filepath);
        try{
            FileWriter fileWriter = new FileWriter(filepath, true);
            CSVWriter csvWriter = new CSVWriter(fileWriter);
            String[] data = {getTime(), wifiname, String.valueOf(wifirssi), String.valueOf(lightValue)};
            csvWriter.writeNext(data);

            csvWriter.close();
            fileWriter.close();

        }catch (IOException e){
            e.printStackTrace();
            Log.w(TAG, "addCSVContent: "+e.toString());
        }

    };

    private String getTime(){
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
//        Log.d(TAG, String.valueOf(hour) + "-" + String.valueOf(minute) + "-" + String.valueOf(second));
        return String.valueOf(hour) + ":" + String.valueOf(minute) + ":" + String.valueOf(second);
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Retrieve the light sensor data
        lightValue = event.values[0];
        // Use the light sensor data as needed
//        Log.d("Light Sensor", "Light value: " + lightValue);
        String lightString = "Light value: " + formatFloat(lightValue, 2);
        lightTextView.setText(lightString);
    }

    private String formatFloat(float value, int decimalPlaces) {
        // Create the decimal format with the desired number of decimal places
        String pattern = "#.";
        for (int i = 0; i < decimalPlaces; i++) {
            pattern += "#";
        }

        DecimalFormat decimalFormat = new DecimalFormat(pattern);
        return decimalFormat.format(value);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes if needed
    }

}