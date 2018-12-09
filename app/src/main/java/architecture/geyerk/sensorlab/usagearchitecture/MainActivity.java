package architecture.geyerk.sensorlab.usagearchitecture;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;

import static architecture.geyerk.sensorlab.usagearchitecture.Constants.*;

public class MainActivity extends AppCompatActivity {

    /*
    This app is intended to layout comprehensively the architecture for multiple different psych apps developed within the sensorlab:
    - activity logger
    - app usage logger
    - introspect
    - retrospect
    - past usage
    - Peg Log
    - Psych app

    This architecture will primarily focused on prospective (future based) logging.

    The initialization of the app operates as follows (initial method called in bracket):
    - detect state of the app (detectState)
    - document what data is being collected & rational (informUser)
    - request password for SQL cypher and encrypted pdf to export (requestPassword)
    - request essential permissions (requestEssentialPermission)
    - start logging of data (logData)

    This other options offered will be directed by the button press listener, this is highlighted in the code.
    Options provided include (initial method called in bracket):
    - export data (export)
    - change password (changePassword)
    - see last data point (presentLastDataPoint)
    - see instructions again (informUser)
     */

    //global values
    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    //broadcast receivers
    BroadcastReceiver serviceListener;


    //initialization
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeSQLCypherLibrary();
        initializeServiceStateListener();
        directNextStageOfInitialization(detectState());
    }

    private void initializeServiceStateListener() {

        serviceListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("dataToReceive", false)){
                    Log.i("FROM service", "data collection on going");
                    //relay that service if functioning properly
                }
                if(intent.getBooleanExtra("dataToDisplay", false)){
                    final String msg = intent.getStringExtra("dataToRelay");
                    Log.i("FROM service", msg);
                    //change string value to msg
                }
            }
        };

        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(serviceListener, new IntentFilter("changeInService"));
    }

    //fix this
    private void initializeSQLCypherLibrary() {
    }

    private int detectState(){
        initializeSharedPreferences();
        int state = 1;

        if(prefs.getBoolean("instructions shown", false)){
            state = 2;
            if(prefs.getBoolean("password provided", false)){
                switch (essentialPermissionHasBeenProvided()){
                    case  Constants.ALL_PERMISSIONS_GIVEN:
                        state = 3;
                        switch(establishDataToAccess()){
                            case Constants.LOGGING_BASIC:
                                if(serviceIsRunning(BackgroundLogging.class)){
                                    state = 7;
                                }
                                break;
                            case Constants.LOGGING_FOREGROUND:
                                if(serviceIsRunning(foregroundLogging.class)){
                                    state = 7;
                                }
                                break;
                            case Constants.LOGGING_NOTIFICATION:
                                if(serviceIsRunning(notificationLogging.class)){
                                    state = 7;
                                }
                                break;
                            case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                                if(serviceIsRunning(notificationAndForegroundLogging.class)){
                                    state = 7;
                                }
                                break;
                        }
                        break;
                    case Constants.USAGE_STATISTICS_PERMISSION_REQUIRED:
                        state = 4;
                        break;

                    case Constants.NOTIFICATIONS_PERMISSION_REQUIRED:
                        state = 5;
                        break;

                    case Constants.MULTIPLE_PERMISSIONS_REQUIRED:
                        state = 6;
                        break;
                }

            }
        }
        return state;
    }

    private int establishDataToAccess() {

        if(!researcherInput.NOTIFICATION_LISTENER_REQUIRED && !researcherInput.USAGE_STATISTICS_REQUIRED){
            return Constants.LOGGING_BASIC;
        }else if(!researcherInput.NOTIFICATION_LISTENER_REQUIRED && researcherInput.USAGE_STATISTICS_REQUIRED){
            return Constants.LOGGING_FOREGROUND;
        }else if (researcherInput.NOTIFICATION_LISTENER_REQUIRED && !researcherInput.USAGE_STATISTICS_REQUIRED){
            return Constants.LOGGING_NOTIFICATION;
        }else{
            return Constants.LOGGING_FOREGROUND_AND_NOTIFICATION;
        }
    }

    private void initializeSharedPreferences() {
        prefs = getSharedPreferences("initialization prefs", MODE_PRIVATE);
        editor = prefs.edit();
        editor.apply();
    }

    private boolean serviceIsRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private int essentialPermissionHasBeenProvided() {

        switch(establishDataToAccess()){
            case Constants.LOGGING_BASIC:
                return ALL_PERMISSIONS_GIVEN;
            case Constants.LOGGING_FOREGROUND:
                if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_GRANTED) {
                    return ALL_PERMISSIONS_GIVEN;
                }else{
                    return USAGE_STATISTICS_PERMISSION_REQUIRED;
                }
            case Constants.LOGGING_NOTIFICATION:
                if(establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_GRANTED){
                    return ALL_PERMISSIONS_GIVEN;
                }else{
                    return NOTIFICATIONS_PERMISSION_REQUIRED;
                }
            case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_GRANTED&&
                        establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_GRANTED){
                    return ALL_PERMISSIONS_GIVEN;
                }else if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_GRANTED&&
                        establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_DENIED){
                    return NOTIFICATIONS_PERMISSION_DENIED;
                }else if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_DENIED&&
                        establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_GRANTED){
                    return USAGE_STATISTICS_PERMISSION_DENIED;
                }else{
                    return MULTIPLE_PERMISSIONS_REQUIRED;
                }
            default:
                return Constants.MULTIPLE_PERMISSIONS_REQUIRED;
        }
    }

    private int establishStateOfUsageStatisticsPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                int mode = 2;
                if(appOpsManager != null){
                    mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
                }
                if(mode == AppOpsManager.MODE_ALLOWED){
                   return USAGE_STATISTICS_PERMISSION_GRANTED;
                }else{
                   return USAGE_STATISTICS_PERMISSION_DENIED;
                }
            }else{
                return USAGE_STATISTICS_PERMISSION_GRANTED;
            }
    }


    public int establishStateOfNotificationListenerPermission() {
        ComponentName cn = new ComponentName(this, notificationLogging.class);
        String flat = Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners");
        if(flat != null && flat.contains(cn.flattenToString())){
            return NOTIFICATIONS_PERMISSION_GRANTED;
        }else{
            return NOTIFICATIONS_PERMISSION_DENIED;
        }
    }


    private void directNextStageOfInitialization(int state){
        switch (state){
            case 1:
                informUser();
                break;
            case 2:
                requestPassword();
                break;
            case 3:
                logData();
                break;
            case 4:
                requestUsagePermission();
                break;
            case 5:
                requestNotificationPermission();
                break;
            case 6:
                //requestUsagePermission and then recalls this method
                requestUsagePermission();
                break;
            case 7:
                Toast.makeText(this, "data logging underway", Toast.LENGTH_SHORT).show();
                break;
        }

    }

    private void requestUsagePermission() {
            AlertDialog.Builder builder;
            builder = new AlertDialog.Builder(MainActivity.this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setTitle("Usage permission")
                    .setMessage("To participate in this experiment you must enable usage stats permission")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                            startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Constants.REQUEST_USAGE_STATISTICS);

                        }
                    })
                    .show();
        }else{
            builder.setTitle("Usage permission")
                    .setMessage("An error has occurred. Please inform the researcher that this app is experiencing a SDK version error.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .show();
        }
    }

    private void requestNotificationPermission() {
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Usage permission")
                    .setMessage("To participate in this experiment you must provide allow the app to listen to the notifications generated by the smartphone.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), Constants.REQUEST_NOTIFICATION_LISTENER);
                        }
                    })
                    .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case Constants.REQUEST_USAGE_STATISTICS:
                directNextStageOfInitialization(detectState());
                break;
            case Constants.REQUEST_NOTIFICATION_LISTENER:
                directNextStageOfInitialization(detectState());
                break;
            case Constants.SHOW_PRIVACY_POLICY:
                directNextStageOfInitialization(detectState());
        }
    }

    private void informUser() {
        StringBuilder toRelayToParticipant = new StringBuilder();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        toRelayToParticipant.append("This application records:")
                .append("\n")
                .append("When your screen is on/off")
                .append("\n")
                .append("When your phone is on/off")
                .append("\n")
                .append("If apps are un/installed")
                .append("\n");

        if(researcherInput.NOTIFICATION_LISTENER_REQUIRED){
            toRelayToParticipant.append("What apps send your notification (but not what the notifications say)")
                    .append("\n");
        }
        if(researcherInput.USAGE_STATISTICS_REQUIRED){
            toRelayToParticipant.append("What app is running in the foreground")
                    .append("\n");
        }

        toRelayToParticipant.append("The data is stored securely on your smartphone.")
                .append("\n")
                .append("To stop data collection and delete all associated files, simply uninstall the application.")
                .append("\n");

        if(researcherInput.USAGE_STATISTICS_REQUIRED || researcherInput.NOTIFICATION_LISTENER_REQUIRED){
            toRelayToParticipant.append("You will now be asked to provide a password to secure your data and approve relevant permissions.")
                    .append("\n");
        }

        if(researcherInput.NOTIFICATION_LISTENER_REQUIRED){
            toRelayToParticipant.append("These permissions will include access to notification listener")
                    .append("\n");
        }
        if(researcherInput.USAGE_STATISTICS_REQUIRED){
            toRelayToParticipant.append("These permissions will include access to usage statistics listener, this will give us access to the know what app is running in the foreground")
                    .append("\n");
        }
        toRelayToParticipant.append("For more information please click on 'view privacy policy'");

        builder.setTitle("usage app")
                .setMessage(toRelayToParticipant)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putBoolean("instructions shown", true)
                                .apply();

                    }
                }).setNegativeButton("View privacy policy", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Uri uri = Uri.parse("https://psychsensorlab.com/privacy-agreement-for-apps/");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
                startActivityForResult(launchBrowser, Constants.SHOW_PRIVACY_POLICY);
            }
        });
        builder.create()
                .show();

    }

    private void requestPassword() {
        LayoutInflater inflater = this.getLayoutInflater();
        //create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("password")
                .setMessage("Please specify a password that is 6 characters in length. This can include letters and/or numbers.")
                .setView(inflater.inflate(R.layout.password_alert_dialog, null))
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Dialog d = (Dialog) dialogInterface;
                        EditText password = d.findViewById(R.id.etPassword);
                        if (checkPassword(password.getText())) {
                            editor.putBoolean("password generated", true);
                            editor.putString("password", String.valueOf(password.getText()));
                            editor.putString("pdfPassword", String.valueOf(password.getText()));
                            editor.apply();
                            directNextStageOfInitialization(detectState());
                        } else {
                            requestPassword();
                            Toast.makeText(MainActivity.this, "The password entered was not long enough", Toast.LENGTH_SHORT).show();
                        }
                    }

                    private boolean checkPassword(Editable text) {
                        return text.length() > 5;
                    }
                });
        builder.create()
                .show();
    }

    private void logData() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                switch (establishDataToAccess()) {
                    case Constants.LOGGING_BASIC:
                        startService(new Intent(this, BackgroundLogging.class));
                        break;
                    case Constants.LOGGING_FOREGROUND:
                        startService(new Intent(this, foregroundLogging.class));
                        break;
                    case Constants.LOGGING_NOTIFICATION:
                        startService(new Intent(this, notificationLogging.class));
                        break;
                    case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                        startService(new Intent(this, notificationAndForegroundLogging.class));
                        break;
                 }
            }else{
                switch (establishDataToAccess()) {
                    case Constants.LOGGING_BASIC:
                        startForegroundService(new Intent(this, BackgroundLogging.class));
                        break;
                    case Constants.LOGGING_FOREGROUND:
                        startForegroundService(new Intent(this, foregroundLogging.class));
                        break;
                    case Constants.LOGGING_NOTIFICATION:
                        startForegroundService(new Intent(this, notificationLogging.class));
                        break;
                    case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                        startForegroundService(new Intent(this, notificationAndForegroundLogging.class));
                        break;
                }
            }
        }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(serviceListener);
    }


    //make async task where the apps are documented and stored in sharedPreferences
    class documentInstalledApps extends AsyncTask<Object, Void, Void>{

        @Override
        protected Void doInBackground(Object[] objects) {

            return null;
        }
    }

    //make async task where the SQL files are packaged into Pdf files

    class packageFiles extends AsyncTask{

        @Override
        protected Object doInBackground(Object[] objects) {
            return null;
        }
    }

}

