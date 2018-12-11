package architecture.geyerk.sensorlab.usagearchitecture;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class notificationAndForegroundLogging extends NotificationListenerService {

    BroadcastReceiver appReceiver, screenReceiver;
    SharedPreferences prefs;

    Handler handler;
    printForegroundTask printForeground;

    UsageStatsManager usm;
    ActivityManager am;

    String currentlyRunningApp, runningApp;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initializeService();
        initializeSQLCipher();
        initializeSharedPreferences();
        initializeHandler();
        initializeBroadcastReceivers();
        if(researcherInput.DOCUMENT_INSTALLED_APPS){
            initializeAppListener();
        }
        return START_STICKY;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        storeData("note added: " +sbn.getPackageName());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        storeData("note removed: " + sbn.getPackageName());
    }

    @SuppressLint("WrongConstant")
    private void initializeHandler() {
        handler = new Handler();
        printForeground = new printForegroundTask();

        currentlyRunningApp = "";
        runningApp = "x";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            usm = (UsageStatsManager) this.getSystemService("usagestats");
        }else{
            am = (ActivityManager)this.getSystemService(getApplicationContext().ACTIVITY_SERVICE);
        }

    }


    private void initializeService() {
        Log.i("SERVICE", "running");

        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT > 26) {
                String CHANNEL_ONE_ID = "sensor.example. geyerk1.inspect.screenservice";
                String CHANNEL_ONE_NAME = "Screen service";
                NotificationChannel notificationChannel = null;
                notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                        CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
                notificationChannel.setShowBadge(true);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                assert manager != null;
                manager.createNotificationChannel(notificationChannel);

                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_background_logging);
                Notification notification = new Notification.Builder(getApplicationContext())
                        .setChannelId(CHANNEL_ONE_ID)
                        .setContentTitle("Recording data")
                        .setContentText("PsychApps is logging data")
                        .setSmallIcon(R.drawable.ic_background_logging)
                        .setLargeIcon(icon)
                        .build();

                Intent notificationIntent = new Intent(getApplicationContext(),MainActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                startForeground(101, notification);
            } else {
                startForeground(101, updateNotification());
            }
        } else {
            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("Recording data")
                    .setContentText("app is logging data")
                    .setContentIntent(pendingIntent).build();

            startForeground(101, notification);
        }
    }

    private void initializeSharedPreferences() {
        prefs = getSharedPreferences("initialization prefs", MODE_PRIVATE);
    }


    private Notification updateNotification() {

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        return new NotificationCompat.Builder(this)
                .setContentTitle("Activity log")
                .setTicker("Ticker")
                .setContentText("data recording a is on going")
                .setSmallIcon(R.drawable.ic_background_logging)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
    }

    private void initializeSQLCipher() {
        SQLiteDatabase.loadLibs(this);
    }

    private void initializeBroadcastReceivers() {

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction() != null){
                    switch (intent.getAction()){
                        case Intent.ACTION_SCREEN_OFF:
                            storeData("screen off");
                            handler.removeCallbacks(documentForegroundTask);
                            break;
                        case Intent.ACTION_SCREEN_ON:
                            storeData("screen on");
                            handler.postDelayed(documentForegroundTask, researcherInput.FREQUENCY_THAT_FOREGROUND_APP_IS_SURVEYED);
                            break;
                        case Intent.ACTION_USER_PRESENT:
                            storeData("user present");
                            break;
                    }
                }
            }
              final Runnable documentForegroundTask = new Runnable() {
                @Override
                public void run() {
                    String appRunningInForeground;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        appRunningInForeground = printForeground.identifyForegroundTaskLollipop(usm, getApplicationContext());
                    } else {
                        appRunningInForeground = printForeground.identifyForegroundTaskUnderLollipop(am);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (Objects.equals(appRunningInForeground, "usage architecture")) {
                            informMain("", false);
                        }
                        if (!Objects.equals(appRunningInForeground, currentlyRunningApp)) {
                            storeData("App: " + appRunningInForeground);
                            currentlyRunningApp = appRunningInForeground;
                        }
                    } else {
                        if(appRunningInForeground == "usage architecture"){
                            informMain("", false);
                        }
                        if (appRunningInForeground == currentlyRunningApp) {
                            storeData("App: " + appRunningInForeground);
                            currentlyRunningApp = appRunningInForeground;
                        }
                    }
                    handler.postDelayed(documentForegroundTask, researcherInput.FREQUENCY_THAT_FOREGROUND_APP_IS_SURVEYED);

                }
            };
        };

        IntentFilter screenReceiverFilter = new IntentFilter();
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenReceiverFilter.addAction(Intent.ACTION_USER_PRESENT);

        registerReceiver(screenReceiver, screenReceiverFilter);
    }

    private void initializeAppListener() {
        appReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction() != null){
                    switch (intent.getAction()){
                        case Intent.ACTION_PACKAGE_ADDED:
                            storeData(updateRecordOfApps() + " added");
                            break;
                        case Intent.ACTION_PACKAGE_REMOVED:
                            storeData(updateRecordOfApps()+ " deleted");
                    }
                }
            }

            private String updateRecordOfApps() {
                SharedPreferences appPrefs = getSharedPreferences("App prefs",MODE_PRIVATE);
                SharedPreferences.Editor appEditor = appPrefs.edit();
                Collection<String> oldApps = new ArrayList<>();
                for (int i = 0; i <appPrefs.getInt("number of apps", 0); i++){
                    oldApps.add(appPrefs.getString("app"+i, "notapp"));
                }

                Collection<String> newApps = returnNewAppList();

                Log.d("newApps", String.valueOf(oldApps));
                Log.d("newApps", String.valueOf(newApps));

                if(oldApps.size() > newApps.size()){
                    oldApps.removeAll(newApps);
                    Log.i("oldApps", "after removal: " + oldApps);
                    if(oldApps.size() == 1){
                        appEditor.clear().apply();
                        appEditor.putString("app" + appPrefs.getInt("number of apps", 0), ((ArrayList<String>) oldApps).get(0))
                                .putInt("number of apps",(appPrefs.getInt("number of apps", 0)+1))
                                .apply();
                        for(int i = 0; i < newApps.size();i++){
                            appEditor.putString("app" + appPrefs.getInt("number of apps", 0), ((ArrayList<String>) newApps).get(i))
                                    .putInt("number of apps",(appPrefs.getInt("number of apps", 0)+1))
                                    .apply();
                        }
                        return String.valueOf(((ArrayList) oldApps).get(0));
                    }else{
                        Log.e("app error", "issue when old apps bigger in size");
                        for(int i = 0; i < oldApps.size();i++){
                            Log.e("app", String.valueOf(((ArrayList) oldApps).get(i)));
                        }
                        return"PROBLEM!!!";
                    }
                }else{
                    newApps.removeAll(oldApps);
                    Log.i("newApps", "after removal: " + newApps);
                    if(newApps.size() == 1){
                        appEditor.clear().apply();
                        appEditor.putString("app" + appPrefs.getInt("number of apps", 0), ((ArrayList<String>) newApps).get(0))
                                .putInt("number of apps",(appPrefs.getInt("number of apps", 0)+1))
                                .apply();
                        for(int i = 0; i < oldApps.size();i++){
                            appEditor.putString("app" + appPrefs.getInt("number of apps", 0), ((ArrayList<String>) oldApps).get(i))
                                    .putInt("number of apps",(appPrefs.getInt("number of apps", 0)+1))
                                    .apply();
                        }

                        return String.valueOf(((ArrayList) newApps).get(0));
                    }else{
                        Log.e("app error", "issue when old apps bigger in size");
                        for(int i = 0; i < newApps.size();i++){
                            Log.e("app", String.valueOf(((ArrayList) newApps).get(i)));
                        }
                        return"PROBLEM!!!";
                    }
                }
            }

            private ArrayList<String> returnNewAppList() {
                ArrayList<String> currentApps = new ArrayList<>();
                PackageManager pm = getApplicationContext().getPackageManager();
                final List<PackageInfo> appInstall= pm.getInstalledPackages(PackageManager.GET_PERMISSIONS|PackageManager.GET_RECEIVERS|
                        PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS);

                for(PackageInfo pInfo:appInstall) {
                    currentApps.add(pInfo.applicationInfo.loadLabel(pm).toString());
                }
                return currentApps;
            }
        };

        IntentFilter appReceiverFilter = new IntentFilter();
        appReceiverFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        appReceiverFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appReceiverFilter.addDataScheme("package");

        registerReceiver(appReceiver, appReceiverFilter);
    }



    private void storeData(String event) {
        SQLiteDatabase database = BackgroundLoggingSQL.getInstance(this).getWritableDatabase(prefs.getString("password", "not to be used"));

        final long time = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.EVENT, event);
        values.put(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TIME, time);

        database.insert(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME, null, values);

        Cursor cursor = database.rawQuery("SELECT * FROM '" + BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME + "';", null);
        Log.d("BackgroundLogging", "Update: " + event + " " + time);
        cursor.close();
        database.close();
        informMain("event", false);
    }

    private void informMain(String message, boolean error) {
        if(!error){
            Intent intent = new Intent("changeInService");
            intent.putExtra("dataToReceive", true);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            Log.i("service", "data sent to main");
        }else {
            Intent intent = new Intent("changeInService");
            intent.putExtra("dataToDisplay", true);
            intent.putExtra("dataToRelay", "error detected: " + message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            Log.i("service", "data sent to main");

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenReceiver);
        unregisterReceiver(appReceiver);
    }
}
