package com.nento.player.app;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nento.player.app.Constants.Companion.*;

public class WifiConnectManager extends Service {

    BluetoothAdapter mBluetoothAdapter = null;
    WifiManager wifiManager;
    List<ScanResult> SSIDresults, SSIDSendtoMobilePhone;
    int SSIDsize = 0;
    String LOGTAG = "BLESERVER";
    int userselectedSSIDIndex = 0;
    String userselectedSSIDpassword = null;
    String BSSID = null;
    boolean firsttime = false, firsttimereadytoaccept = true;
    BluetoothDevice guserselectedbluetoothdevice=null;
    String connectedSSIDName;

    public static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    public static final UUID MY_UUID_REMOTE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    String MycircleSignature = "kjgaxyfxmfaxef4efefx24fe2xfef4exfexgrg2x4y5v2uvi2ni27ni";

    ActivityResultLauncher<Intent> bluetoothActivityResultLauncher;
    public static final String NAME = "WIFIBluetoothServer";
    public static final String REMOTE_NAME = "RemoteBluetoothServer";
    private String[] REQUIRED_PERMISSIONS;
    ActivityResultLauncher<String[]> rpl;

    int WIFI = 0;
    int REPORT = 1;
    int DISCONNECT_WIFI = 2;
    int FORGET_WIFI = 3;

    IBinder mBinder = new LocalBinder();
    InternetChangeReceiver internetChangeReceiver;

    @Override
    public void onCreate() {
        Toast.makeText(this, "My Service Created", Toast.LENGTH_LONG).show();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getmyservicecontext(), "No bluetooth device...", Toast.LENGTH_LONG).show();
        } else {
            Method setscanmodemethod = null,setDiscovermethod = null;

            try {
                setscanmodemethod = mBluetoothAdapter.getClass().getMethod("setScanMode", int.class);
                setDiscovermethod = mBluetoothAdapter.getClass().getMethod("getDiscoverableTimeout");
            } catch (SecurityException | NoSuchMethodException e) {
                e.printStackTrace();
            }

            try {
                setscanmodemethod.invoke(mBluetoothAdapter,
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
                int value = (int) setDiscovermethod.invoke(mBluetoothAdapter);

                int blediscoverablemode = mBluetoothAdapter.getScanMode();

            } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        wifiManager = (WifiManager) getmyservicecontext().getApplicationContext().getSystemService(WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(getmyservicecontext(), "Turning WiFi ON...", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

        registerWifiBCReceiver();

        getmyservicecontext().getApplicationContext().registerReceiver(mWifiScanReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        getmyservicecontext().getApplicationContext().registerReceiver(mWifiStateReceiver, filter);

        wifiManager.startScan();

        startServer();

        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        getmyservicecontext().getApplicationContext().registerReceiver(broadCastReceiver,intentFilter);

        internetChangeReceiver = new InternetChangeReceiver();

        getCurrentConnectionInfo(getmyservicecontextthis());

    }

    void registerWifiBCReceiver() {
        getmyservicecontext().getApplicationContext().registerReceiver(mWifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }
    void unregisterWIFIBCReceiver() {
        getmyservicecontext().getApplicationContext().unregisterReceiver(mWifiScanReceiver);
    }

    private final BroadcastReceiver broadCastReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent1) {
            String action = intent1.getAction();
            if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
                BluetoothDevice device = intent1.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                try {

                    device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
                    abortBroadcast();

                    Toast.makeText(getmyservicecontext().getApplicationContext(), "Device connected: " + device.getName(), Toast.LENGTH_LONG).show();

                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public WifiConnectManager getServerInstance() {
            return WifiConnectManager.this;
        }
    }

    Context getmyservicecontext(){
        return this;
    }

    Context getmyservicecontextthis(){
        return this.getApplicationContext();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
        getmyservicecontext().getApplicationContext().unregisterReceiver(mWifiScanReceiver);
        getmyservicecontext().getApplicationContext().unregisterReceiver(mWifiStateReceiver);

    }


    @SuppressLint("MissingPermission")
    public void setbluetoothdevicename(String nbtname) {
        if (mBluetoothAdapter != null) {
            if(!mBluetoothAdapter.getAddress().equals(nbtname)) {
                Toast.makeText(this, "Bluetooth name changed to: " + nbtname, Toast.LENGTH_LONG).show();
                mBluetoothAdapter.setName(nbtname);
            }
        }
    }

    public void startWifiDiscovery() {
        if (wifiManager != null) {
            wifiManager.startScan();
        }
    }

    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
         /*   boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                if( SSIDresults!= null && SSIDresults.size() != 0) {
                    SSIDresults.clear();
                }
                SSIDresults = wifiManager.getScanResults();
                SSIDsize = SSIDresults.size();
                Log.d(LOGTAG, "WifiScanReceiver: " + SSIDsize);
            }*/

            if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                if( SSIDresults!= null && SSIDresults.size() != 0) {
                    SSIDresults.clear();
                }
                SSIDresults = wifiManager.getScanResults();
                SSIDsize = SSIDresults.size();
            }
        }
    };

    private final BroadcastReceiver mWifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {

            if (firsttime == false) {
                firsttime = true;
                return;
            }
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info != null) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    String ssid = wifiInfo.getSSID();
                    connectedSSIDName  = ssid;
                    if (info.isConnected()) {
                        if(guserselectedbluetoothdevice!=null) {
                            new Thread(new ConnectThread(guserselectedbluetoothdevice, true)).start();
                        }
                        Toast.makeText(getmyservicecontextthis(), "Wifi Connected successfully: CONNECTIVITY_ACTION " + ssid, Toast.LENGTH_LONG).show();
                        getCurrentConnectionInfo(getmyservicecontextthis());

                    } /*else {
                        if(guserselectedbluetoothdevice!=null) {
                            new Thread(new ConnectThread(guserselectedbluetoothdevice, false)).start();
                        }
                        Toast.makeText(getmyservicecontextthis(), "Wifi connection failed: CONNECTIVITY_ACTION" + ssid, Toast.LENGTH_LONG).show();
                    }*/
                }

            } else {
                if (intent.getAction().equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    if (intent.hasExtra(WifiManager.EXTRA_SUPPLICANT_ERROR)) {
                        if(guserselectedbluetoothdevice!=null) {
                            new Thread(new ConnectThread(guserselectedbluetoothdevice, false)).start();
                        }
                        Toast.makeText(getmyservicecontextthis(), "Wifi Connection failed: SUPPLICANT_STATE_CHANGED_ACTION", Toast.LENGTH_LONG).show();
                    }
                } else if (intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                    if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                        if(guserselectedbluetoothdevice!=null) {
                            new Thread(new ConnectThread(guserselectedbluetoothdevice, true)).start();
                        }
                        Toast.makeText(getmyservicecontextthis(), "Wifi Connected successfully:SUPPLICANT_CONNECTION_CHANGE_ACTION", Toast.LENGTH_LONG).show();
                    } else {
                        // wifi connection was lost
                        if(guserselectedbluetoothdevice!=null) {
                            new Thread(new ConnectThread(guserselectedbluetoothdevice, false)).start();
                        }
                        Toast.makeText(getmyservicecontextthis(), "wifi connection was lost:SUPPLICANT_CONNECTION_CHANGE_ACTION", Toast.LENGTH_LONG).show();
                    }
                }
            }

        }
    };

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }
    });
    public void mkmsg(String str) {
        //handler junk, because thread can't update screen!
    }

    public  String getCurrentConnectionInfo(Context context) {
        String ssid = null;
        int speed = 0,frequency;
        String signallevel;
        String SecurityType="";
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        List<ScanResult> networkList = wifiManager.getScanResults();

        if (networkInfo.isConnected()) {
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && ! TextUtils.isEmpty(connectionInfo.getSSID())) {
                ssid = connectionInfo.getSSID();
                frequency = connectionInfo.getFrequency();
                int rssi = wifiManager.getConnectionInfo().getRssi();
                int level = WifiManager.calculateSignalLevel(rssi, 5);
                String currentSSID = connectionInfo.getSSID();
                currentSSID = currentSSID.replace("\"", "");

                if (networkList != null) {
                    for (ScanResult network : networkList) {
                        //check if current connected SSID
                        if (currentSSID.equals(network.SSID)) {
                            //get capabilities of current connection
                            String capabilities = network.capabilities;

                            if (capabilities.toUpperCase().contains("WEP")) {
                                SecurityType = "WEP";
                            } else if (capabilities.toUpperCase().contains("WPA")
                                    || capabilities.toUpperCase().contains("WPA2")) {
                                SecurityType = "WPA";
                            } else {
                                SecurityType = "Open";
                            }
                        }
                    }
                }


            }
        }
        return ssid;
    }

    public void startServer() {
        new Thread(new AcceptThread()).start();
        //new Thread(new RemoteKeyAcceptThread()).start();
    }

    String getAvailableSSID() {

        SSIDSendtoMobilePhone = new ArrayList<>(SSIDresults);
        try {
            JSONObject SSID = new JSONObject();
            SSID.put("TodalSSID", SSIDsize);
            ArrayList<String> list = new ArrayList<String>();
            for (int i = 0; i < SSIDsize; ++i) {
                list.add(SSIDSendtoMobilePhone.get(i).SSID);
            }
            SSID.put("SSIDArray", new JSONArray(list));
            return SSID.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    String getcurrentstatus() {
        String ssid = "";
        int signallevel = 0 , Frequency =0 ,Linkspeed =0;
        List<ScanResult> networkList = wifiManager.getScanResults();
        String SecurityType = "";

        try {
            ConnectivityManager connManager = (ConnectivityManager) getmyservicecontextthis().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo.isConnected()) {
                final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null && ! TextUtils.isEmpty(connectionInfo.getSSID())) {
                    ssid = connectionInfo.getSSID();

                    int rssi = wifiManager.getConnectionInfo().getRssi();
                    signallevel = WifiManager.calculateSignalLevel(rssi, 5);
                    Frequency = connectionInfo.getFrequency();
                    Linkspeed = connectionInfo.getLinkSpeed();

                    String currentSSID = connectionInfo.getSSID();
                    currentSSID = currentSSID.replace("\"", "");

                    if (networkList != null) {
                        for (ScanResult network : networkList) {
                            //check if current connected SSID
                            if (currentSSID.equals(network.SSID)) {
                                //get capabilities of current connection
                                String capabilities = network.capabilities;

                                if (capabilities.toUpperCase().contains("WEP")) {
                                    SecurityType = "WEP";
                                } else if (capabilities.toUpperCase().contains("WPA")
                                        || capabilities.toUpperCase().contains("WPA2")) {
                                    SecurityType = "WPA";
                                } else {
                                    SecurityType = "Open";
                                }
                            }
                        }
                    }
                }
            }

            JSONObject currentstatus = new JSONObject();
            currentstatus.put("net_status", internetChangeReceiver.isInternetAvailable(getmyservicecontextthis()));
            currentstatus.put("screen_ID", Constants.Companion.getScreenID());
            currentstatus.put("ssid", ssid);
            currentstatus.put("level", signallevel);
            currentstatus.put("SecurityType", SecurityType);
            currentstatus.put("Frequency", Frequency);
            currentstatus.put("Linkspeed", Linkspeed);


            return currentstatus.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    String ReceiveMessage(BluetoothSocket socket) {
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        String message = "";
        try {
            InputStream instream = socket.getInputStream();
            int bytesRead = -1;

            while (true) {
                message = "";
                bytesRead = instream.read(buffer);
                if (bytesRead != -1) {
                    while ((bytesRead==bufferSize)&&(buffer[bufferSize-1] != 0)) {
                        message = message + new String(buffer, 0, bytesRead);
                        bytesRead = instream.read(buffer);
                    }
                    message = message + new String(buffer, 0, bytesRead - 1);

                    mkmsg("received a message:\n" + message + "\n");

                    if( bufferSize != bytesRead ) {
                        break;
                    }
                    instream = socket.getInputStream();
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return message;
    }

    void getSSIDPassword(String JsonString) {
        try {
            JSONObject jsonObject = new JSONObject(JsonString);
            userselectedSSIDIndex    = jsonObject.getInt("SSID_Index");
            userselectedSSIDpassword = (String) jsonObject.getString("SSID_Password");

            if(userselectedSSIDIndex!= -1 && !userselectedSSIDpassword.equals("")) {
                connectToNetwork(SSIDSendtoMobilePhone.get(userselectedSSIDIndex).SSID,
                        userselectedSSIDpassword,
                        SSIDSendtoMobilePhone.get(userselectedSSIDIndex).capabilities);
            } else {
            }
        } catch (JSONException e){
            e.printStackTrace();
        }
    }

    boolean disconnectNetwork(){
        boolean isDisconnected = wifiManager.disconnect();
        return  isDisconnected;
    }


    boolean RemoveWiFi(String SSID) {
        WifiManager mWifiManager = (WifiManager) getmyservicecontext().
                getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration mWifiConfig = new WifiConfiguration();

        disconnectNetwork();

        List<WifiConfiguration> conlist = (List<WifiConfiguration>)mWifiManager.getConfiguredNetworks();
        for (int i = 0; i < conlist.size(); i++) {
            conlist.get(i).SSID = conlist.get(i).SSID.replace("\"", "");
            if (SSID.equals(conlist.get(i).SSID)) {
                boolean isDisconnected = mWifiManager.removeNetwork(conlist.get(i).networkId);
                mWifiManager.saveConfiguration();
                return isDisconnected;
            }
        }
        return true;
    }

    private boolean connectToNetwork(String networkSSID,
                                     String networkPass,
                                     String networkCapabilities) {


        //  RemoveWiFi();

        try {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + networkSSID + "\"";   // Please note the quotes. String should contain ssid in quotes
            conf.status = WifiConfiguration.Status.ENABLED;
            conf.priority = 40;

            if (networkCapabilities.toUpperCase().contains("WEP")) {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                conf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                conf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

                if (networkPass.matches("^[0-9a-fA-F]+$")) {
                    conf.wepKeys[0] = networkPass;
                } else {
                    conf.wepKeys[0] = "\"".concat(networkPass).concat("\"");
                }

                conf.wepTxKeyIndex = 0;

            } else if (networkCapabilities.toUpperCase().contains("WPA")) {

                conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

                conf.preSharedKey = "\"" + networkPass + "\"";

            } else {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                conf.allowedAuthAlgorithms.clear();
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            }

            WifiManager wifiManager = (WifiManager) getmyservicecontext().
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int networkId = wifiManager.addNetwork(conf);

            if( networkId != -1 ) {
                @SuppressLint("MissingPermission")
                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration i : list) {
                    if (i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {

                        boolean isDisconnected = wifiManager.disconnect();
                        Log.v(LOGTAG, "isDisconnected : " + isDisconnected);

                        boolean isEnabled = wifiManager.enableNetwork(i.networkId, true);
                        Log.v(LOGTAG, "isEnabled : " + isEnabled);

                        boolean isReconnected = wifiManager.reconnect();
                        Log.v(LOGTAG, "isReconnected : " + isReconnected);

                        break;
                    }
                }
            } else {
                new Handler(getMainLooper()).post(new Runnable() {
                    public void run() {
                        Toast.makeText(getmyservicecontext().getApplicationContext(), "Incorrect WIFI config. Try again", Toast.LENGTH_SHORT).show();
                    }
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            // Create a new listening server socket
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                mkmsg("Failed to start server\n");
            }
            mmServerSocket = tmp;
        }
        private void sendMessage(BluetoothSocket socket, String msg) {
            OutputStream outStream;
            try {
                outStream = socket.getOutputStream();
                byte[] byteString = (msg + " ").getBytes();
                byteString[byteString.length - 1] = 0;
                outStream.write(byteString);
            } catch (IOException e) {
            }
        }

        public void run() {
            while (true) {
                mkmsg("waiting on accept:" + firsttimereadytoaccept);
                if(firsttimereadytoaccept) {
                    new Handler(getMainLooper()).post(new Runnable() {
                        public void run() {
                            Toast.makeText(getmyservicecontext(), "Ready to accept clients", Toast.LENGTH_LONG).show();
                            firsttimereadytoaccept = false;
                        }
                    });
                }
                BluetoothSocket socket = null;
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    mkmsg("Failed to accept\n");
                    continue;
                }

                if (socket != null) {
                    mkmsg("Connection made\n");
                    mkmsg("Remote device address: " + socket.getRemoteDevice().getAddress() + "\n");
                    guserselectedbluetoothdevice = socket.getRemoteDevice();
                    try {
                        mkmsg("Attempting to receive a message ...\n");
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String str = in.readLine();

                        JSONObject obj = new JSONObject(str);
                        String key = obj.getString("key");
                        int reason = obj.getInt("reason");

                        mkmsg("received a message: key: " + str + " Reason: " + reason);

                        if (MycircleSignature.equals(key)) {
                            mkmsg("Valid client proceed further:\n" + str + "\n");
                        } else {
                            mkmsg("InValid client not proceed further:\n" + str + "\n");
                            socket.close();
                            continue;
                        }

                        if (WIFI == reason) {
                            mkmsg("Attempting to send message ...\n");
                            sendMessage(socket, getAvailableSSID());

                            mkmsg("Attempting to receive a message ...\n");
                            String password = ReceiveMessage(socket);
                            mkmsg("password received." + password + "\n");
                            if (!password.equals("")) {
                                getSSIDPassword(password);
                            } else {
                                mkmsg("Please enter vaild password.\n");
                            }
                        } else if (REPORT == reason) {
                            mkmsg("Attempting to send current status ...\n");
                            sendMessage(socket, getcurrentstatus());
                            mkmsg("Sent current status  ...\n");
                        } else if (DISCONNECT_WIFI == reason) {
                            mkmsg("Attempting to Disconnect WIFI ...\n");

                            sendMessage(socket, Boolean.toString(disconnectNetwork()));
                            mkmsg("Sent current status  ...\n");
                        } else if (FORGET_WIFI == reason) {
                            String SSID = obj.getString("ConnectedSSID");
                            SSID = SSID.replace("\"", "");
                            mkmsg("Attempting to Forget WIFI SSID: " + SSID);
                            sendMessage(socket, Boolean.toString(RemoveWiFi(SSID)));
                            mkmsg("Sent current status  ...\n");
                        }
                        mkmsg("We are done, closing connection\n");
                        wifiManager.startScan();
                    } catch (Exception e) {
                        mkmsg("Error happened sending/receiving\n");
                        e.printStackTrace();

                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            mkmsg("Unable to close socket" + e.getMessage() + "\n");
                        }
                    }
                } else {
                    mkmsg("Made connection, but socket is null\n");
                }
                mkmsg("Server ending \n");
            }
        }
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                mkmsg( "close() of connect socket failed: "+e.getMessage() +"\n");
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private boolean wifi_status = false;
        BluetoothSocket bluetoothSocket = null;
        String ssid = "";
        int speed = 0;
        int signallevel = 0 , Frequency =0 ,Linkspeed =0;
        List<ScanResult> networkList = null;
        String SecurityType = "";

        UUID WIFI_REPORT_UUID = UUID.fromString("6cca6f3e-4fdc-11ed-bdc3-0242ac120002");

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device, boolean wifi_connection) {
            mkmsg("WifiConnectServiceManager ConnectThread Wifi Status: " + wifi_connection );
            mmDevice = device;
            wifi_status = wifi_connection;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(WIFI_REPORT_UUID);
            } catch (IOException e) {
                mkmsg("WifiConnectServiceManager connection failed: " + e.getMessage() + "\n");
            }
            bluetoothSocket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            mkmsg("WifiConnectServiceManager Client running\n");
            mBluetoothAdapter.cancelDiscovery();
            try {
                bluetoothSocket.connect();
            } catch (IOException e) {
                mkmsg("WifiConnectServiceManager Connect failed\n");
                try {
                    bluetoothSocket.close();
                    bluetoothSocket = null;
                } catch (IOException e2) {
                    mkmsg("unable to close() socket during connection failure: " + e2.getMessage() + "\n");
                    bluetoothSocket = null;
                }
            }

            if (bluetoothSocket != null) {
                try {
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(bluetoothSocket.getOutputStream())), true);
                    mkmsg("WifiConnectServiceManager Attempting to send message ...\n");

                    ConnectivityManager connManager = (ConnectivityManager) getmyservicecontextthis().getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    if (networkInfo.isConnected()) {
                        final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                        if (connectionInfo != null && ! TextUtils.isEmpty(connectionInfo.getSSID())) {
                            networkList = SSIDSendtoMobilePhone;
                            ssid = connectionInfo.getSSID();
                            speed = connectionInfo.getLinkSpeed();
                            int rssi = wifiManager.getConnectionInfo().getRssi();
                            signallevel = WifiManager.calculateSignalLevel(rssi, 5);

                            Frequency = connectionInfo.getFrequency();
                            Linkspeed = connectionInfo.getLinkSpeed();

                            String currentSSID = connectionInfo.getSSID();
                            currentSSID = currentSSID.replace("\"", "");

                            if (networkList != null) {
                                for (ScanResult network : networkList) {
                                    //check if current connected SSID
                                    if (currentSSID.equals(network.SSID)) {
                                        //get capabilities of current connection
                                        String capabilities = network.capabilities;

                                        if (capabilities.toUpperCase().contains("WEP")) {
                                            SecurityType = "WEP";
                                        } else if (capabilities.toUpperCase().contains("WPA")
                                                || capabilities.toUpperCase().contains("WPA2")) {
                                            SecurityType = "WPA";
                                        } else {
                                            SecurityType = "Open";
                                        }
                                    }
                                }
                            }

                        }
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("wifi_status", wifi_status);
                    obj.put("ssid", ssid);
                    obj.put("level", signallevel);
                    obj.put("SecurityType", SecurityType);
                    obj.put("Frequency", Frequency);
                    obj.put("Linkspeed", Linkspeed);

                    out.println(obj.toString());
                    out.flush();

                    mkmsg("WifiConnectServiceManager Message sent...\n");

                } catch (Exception e) {
                    mkmsg("WifiConnectServiceManager Error happened sending/receiving\n");
                    e.printStackTrace();
                } /*finally {
                    try {
                        mkmsg("We are done, closing connection\n");
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        mkmsg("Unable to close socket" + e.getMessage() + "\n");
                    }
                }*/
            } else {
                mkmsg("WifiConnectServiceManager Made connection, but socket is null\n");
                new Handler(getMainLooper()).post(new Runnable() {
                    public void run() {
                        Toast.makeText(getmyservicecontextthis(), "Connection failed. Failed to report wifi status to Mobile", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            mkmsg("WifiConnectServiceManager Client ending \n");
        }
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                mkmsg("close() of connect socket failed: " + e.getMessage() + "\n");
            }
        }
    }
}
