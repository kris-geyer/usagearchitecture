package architecture.geyerk.sensorlab.usagearchitecture;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static architecture.geyerk.sensorlab.usagearchitecture.Constants.*;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AsyncResponse {

    /*
    This app is intended to layout comprehensively the architecture for multiple different psych apps developed within the sensorlab:
    - activity logger
    - app usage logger
    - Psych app

    This architecture will primarily focused on prospective (future based) logging.

    The initialization of the app operates as follows (initial method called in bracket):
    - detect state of the app (detectState)
    - document what data is being collected & rational (informUser)
    - request password for SQL cypher and encrypted pdf to export (requestPassword)
    - request essential permissions (requestEssentialPermission)
    - start logging of data (logData)
    //more detailed account provided in relevant code

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

    //progress bar
    ProgressBar progressBar;

    TextView statusTV;


    //initialization
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeSQLCypherLibrary();
        initializeSharedPreferences();
        initializeServiceStateListener();
        directNextStageOfInitialization(detectState());
        determineUI();
        initializeVisualComponents();
        initializeButtons();
    }

    /**
     * ----------------------------------------Initialization---------------------------------------
     */


    //loads library for the SQL cypher library to function
    private void initializeSQLCypherLibrary() { SQLiteDatabase.loadLibs(this);}

    //initializes the shared preferences & the editor of the preferences.
    //shared preferences is data that is not erased regardless of the closure of the app.
    //This is required to document the state of the initialization of the app, otherwise,
    //every time that the user opened the app, they would have to provide a new password and be
    //greeted by the same message explaining what the app does.
    private void initializeSharedPreferences() {
        prefs = getSharedPreferences("initialization prefs", MODE_PRIVATE);
        editor = prefs.edit();
        editor.apply();
    }

    //this sets up some architecture in the code that will listen for significant signals from the background logging
    //this will indicate that the data collection is functioning or there is a problem, in a way that the participant can be informed
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

    /**
     * --------------------------------------Detect state of phone-----------------------------------
     */

    /*here we run a number of assessments to identify what is the state of the app. It returns an integer which corresponds with a particular state.
    Here is a list of what the states represent:
    1 - The user has not confirmed that they've read the introduction message (they may have seen it but pressed on the privacy policy)
    2 - The user has confirmed that they've read the introduction message
    3 - The user has provided the password and in the background there will be a documentation of the apps installed on the device &/or the permissions employed
    Please note that this an optional feature, researchers will be able to indicate if they wish to include this.
    4 - The permissions that are required for the functioning of the app are provided.
    5 - The user needs to provide usage permission
    6 - The user needs to provide notification permissions
    7 - The user needs to provide multiple permissions
    8 - The data is being logged
    9 - Some error was experienced in detecting permissions
    */
    private int detectState(){
        int state = 1;

        if(prefs.getBoolean("instructions shown", false)){
            state = 2;
            if(prefs.getBoolean("password generated", false)){
                if(researcherInput.DOCUMENT_INSTALLED_APPS|| researcherInput.PERMISSIONS_TO_BE_DOCUMENTED){
                    if(prefs.getBoolean("context provided", false)){
                        state = detectPermissionsState();
                    }else{
                        state = 3;
                    }
                }else{
                    state = detectPermissionsState();
                }
            }
        }
        return state;
    }

    //This establishes what permission is required based on what the researchers have indicated that they wish to include in their study.
    private int detectPermissionsState() {
        int permissionState;
        switch (essentialPermissionHasBeenProvided()) {
            case Constants.ALL_PERMISSIONS_GIVEN:

                if(researcherInput.RETROACTIVITY_ESTABLISH_DURATION_OF_USE){
                    String directory = (String.valueOf(this.getFilesDir()) + File.separator);
                    File pastUsage = new File(directory + File.separator + Constants.PAST_USAGE_NAME);
                    if(pastUsage.exists()){
                        permissionState = 4;
                    }else{
                        permissionState = 9;
                    }

                }else{
                    switch (establishDataToAccess()) {
                        case Constants.LOGGING_BASIC:
                            if (serviceIsRunning(BackgroundLogging.class)) {
                                permissionState = 8;
                            }
                            break;
                        case Constants.LOGGING_FOREGROUND:
                            if (serviceIsRunning(foregroundLogging.class)) {
                                permissionState = 8;
                            }
                            break;
                        case Constants.LOGGING_NOTIFICATION:
                            if (serviceIsRunning(notificationLogging.class)) {
                                permissionState = 8;
                            }
                            break;
                        case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                            if (serviceIsRunning(notificationAndForegroundLogging.class)) {
                                permissionState = 8;
                            }
                            break;

                }

                }
                break;
            case Constants.USAGE_STATISTICS_PERMISSION_REQUIRED:
                permissionState = 5;
                break;

            case Constants.NOTIFICATIONS_PERMISSION_REQUIRED:
                permissionState = 6;
                break;

            case Constants.MULTIPLE_PERMISSIONS_REQUIRED:
                permissionState = 7;
                break;
            default:
                permissionState = 10;
        }
        Log.i("permission", "state: " +permissionState);
        return permissionState;
    }
    //establishes what data that the researcher wishes to access.
    private int establishDataToAccess() {
        //This if statement will return true if the researcher does not wish to record any usage statistics or notification
        if(!researcherInput.NOTIFICATION_LISTENER_REQUIRED && !researcherInput.USAGE_STATISTICS_REQUIRED){
            //therefore the method will indicate that the only want to log the basics
            return Constants.LOGGING_BASIC;
        }else if(!researcherInput.NOTIFICATION_LISTENER_REQUIRED && researcherInput.USAGE_STATISTICS_REQUIRED){
            return Constants.LOGGING_FOREGROUND;
        }else if (researcherInput.NOTIFICATION_LISTENER_REQUIRED && !researcherInput.USAGE_STATISTICS_REQUIRED){
            return Constants.LOGGING_NOTIFICATION;
        }else {
            Log.i("LFAN1", "accessed");
            return Constants.LOGGING_FOREGROUND_AND_NOTIFICATION;
        }
    }

    //based on what logging is required the app will see if the required permission has been granted
    private int essentialPermissionHasBeenProvided() {
        //here the previous method is sampled to see what data is requested.
        switch(establishDataToAccess()){
            case Constants.LOGGING_BASIC:
                return ALL_PERMISSIONS_GIVEN;
            case Constants.LOGGING_FOREGROUND:
                if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_GRANTED) {
                    return ALL_PERMISSIONS_GIVEN;
                }else{
                    return USAGE_STATISTICS_PERMISSION_REQUIRED;
                }
            //in the case of logging notification being required, the app will see if listening to notifications is possible.
            //If the value returned is NOTIFICATION_PERMISSION_GRANTED then this method will relay that all the permissions are provided.
            case Constants.LOGGING_NOTIFICATION:
                if(establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_GRANTED){
                    return ALL_PERMISSIONS_GIVEN;
                }else{
                    return NOTIFICATIONS_PERMISSION_REQUIRED;
                }
            case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                Log.i("LFAN", "accessed");
                if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_GRANTED&&
                        establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_GRANTED){
                    return ALL_PERMISSIONS_GIVEN;
                }else if(establishStateOfUsageStatisticsPermission() == USAGE_STATISTICS_PERMISSION_GRANTED&&
                        establishStateOfNotificationListenerPermission() != NOTIFICATIONS_PERMISSION_GRANTED){
                    return NOTIFICATIONS_PERMISSION_REQUIRED;
                }else if(establishStateOfUsageStatisticsPermission() != USAGE_STATISTICS_PERMISSION_GRANTED&&
                        establishStateOfNotificationListenerPermission() == NOTIFICATIONS_PERMISSION_GRANTED){
                    return USAGE_STATISTICS_PERMISSION_REQUIRED;
                }else{
                    return MULTIPLE_PERMISSIONS_REQUIRED;
                }
            default:
                return Constants.MULTIPLE_PERMISSIONS_REQUIRED;
        }
    }

    //detects if the background logging behaviour is running, this will not detect if data is being collected.
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

    //establishes if the usage statistics permissions are provided
    private int establishStateOfUsageStatisticsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = 2;
            if(appOpsManager != null){
                mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
            }
            if(mode == AppOpsManager.MODE_ALLOWED){
                return Constants.USAGE_STATISTICS_PERMISSION_GRANTED;
            }else{
                return Constants.USAGE_STATISTICS_PERMISSION_DENIED;
            }
        }else{
            return Constants.USAGE_STATISTICS_PERMISSION_GRANTED;
        }
    }

    //establishes if the notification permissions are provided
    public int establishStateOfNotificationListenerPermission() {
        ComponentName cn = new ComponentName(this, notificationLogging.class);
        String flat = Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners");
        if(flat == null || flat.contains(cn.flattenToString())){
            return Constants.NOTIFICATIONS_PERMISSION_GRANTED;
        }else{
            return Constants.NOTIFICATIONS_PERMISSION_DENIED;
        }
    }

    /**
     * Direct the users action dependent on the state of the app.
     */

    //once the state of the app is established then the app directs it to take the appropriate action
    private void directNextStageOfInitialization(int state){
        switch (state){
            //the app has indicated that the participant has not documented that they have accessed the app.
            //Therefore they will be provided a notification of the app
            case 1:
                informUser();
                break;
            case 2:
                requestPassword();
                break;
            case 3:
                attemptToRecordInstalledApps();
                break;
            case 4:
                logData();
                break;
            case 5:
                requestUsagePermission();
                break;
            case 6:
                requestNotificationPermission();
                break;
            case 7:
                //requestUsagePermission and then recalls this method
                requestUsagePermission();
                break;
            case 8:
                final int dataToCollect =establishDataToAccess();
                if(dataToCollect == LOGGING_NOTIFICATION || dataToCollect == LOGGING_FOREGROUND_AND_NOTIFICATION){
                    logData();
                }else{
                    Toast.makeText(this, "data logging underway", Toast.LENGTH_SHORT).show();
                }
                break;
            case 9:
                documentUsageRetroactively();
                break;
            case 10:
                Toast.makeText(this, "There has been some problem regarding the permissions, please inform the researcher.", Toast.LENGTH_LONG).show();
                break;
        }
    }

    //The methods are quite self explanatory in this section based on the name

    //The user will be presented with a tailored message dependent on the data that the researcher is requesting.
    //Access to the privacy policy of the sensor lab is also provided.
    private void informUser() {
        StringBuilder toRelayToParticipant = new StringBuilder();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        toRelayToParticipant.append("This application records:").append("\n")
                .append("\n")
                .append("- When your screen is on/off").append("\n")
                .append("\n")
                .append("- When your phone is on/off").append("\n")
                .append("\n");


        if(researcherInput.NOTIFICATION_LISTENER_REQUIRED){
            toRelayToParticipant.append("- What apps send your notification (but not what the notifications say)").append("\n")
                    .append("\n");
        }
        if(researcherInput.USAGE_STATISTICS_REQUIRED){
            toRelayToParticipant.append("- What app is running in the foreground").append("\n")
                    .append("\n")
                    .append("- If installation or un-installation of apps occurs").append("\n")
                    .append("\n");
        }

        toRelayToParticipant.append("The data is stored securely on your smartphone.").append("\n")
                .append("\n")
                .append("To stop data collection and delete all associated files, simply uninstall the application.").append("\n")
                .append("\n");

        if(researcherInput.USAGE_STATISTICS_REQUIRED || researcherInput.NOTIFICATION_LISTENER_REQUIRED){
            toRelayToParticipant.append("You will now be asked to provide a password to secure your data and approve relevant permissions.").append("\n")
                    .append("\n");
        }

        if(researcherInput.NOTIFICATION_LISTENER_REQUIRED){
            toRelayToParticipant.append("These permissions will include access to notification listener").append("\n")
                    .append("\n");
        }
        if(researcherInput.USAGE_STATISTICS_REQUIRED){
            toRelayToParticipant.append("These permissions will include access to usage statistics listener, this will give us access to the know what app is running in the foreground").append("\n")
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
                        directNextStageOfInitialization(detectState());


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

    //password is requested that is 6 character or longer is requested. This is used to secure the encryption of the Pdf and the SQL.
    //Only the pdf encryption can be changed when the password is changed.
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

    //Detects what data is required to be logged by the researchers and will start a targeted background operation as a result
    //These will be starting foreground services (https://developer.android.com/guide/components/services)
    //How to do this is different for phones which run on oreo (or later) or earlier ones.
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
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP){
                        startService(new Intent(this, notificationLogging.class));
                    }else{
                        statusTV.setText(R.string.too_old);
                    }
                    break;
                case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP){
                        startService(new Intent(this, notificationLogging.class));
                    }else{
                        statusTV.setText(R.string.too_old);
                    }
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
                    startForegroundService(new Intent(this, notificationLogging.class));
                    break;
            }
        }
    }

    
    private void attemptToRecordInstalledApps() {
        statusTV.setText("Status: documenting installed apps and permissions");
        documentInstalledApps DIA  = new documentInstalledApps(this);
        DIA.execute(this, getApplicationContext());
    }

    //make async task where the apps are documented and stored in sharedPreferences
    public class documentInstalledApps extends AsyncTask<Object, Integer, Boolean>{


        Context mContextApps;
        SharedPreferences appPrefs;
        SharedPreferences.Editor appEditor;

        PackageManager pm;
        // you may separate this or combined to caller class.

        documentInstalledApps(AsyncResponse delegate) {
            this.delegate = delegate;
        }

        AsyncResponse delegate = null;


        @Override
        protected Boolean doInBackground(Object[] objects) {
            initializeObjects(objects);
            return insertAppsAndPermissions(recordInstalledApps(objects));
        }

        private void initializeObjects(Object[] objects) {
            mContextApps = (Context) objects[0];
            appPrefs = getSharedPreferences("App prefs", MODE_PRIVATE);
            appEditor = appPrefs.edit();
            appEditor.apply();
        }

        private HashMap<String, ArrayList> recordInstalledApps(Object[] objects) {
            HashMap<String, ArrayList> appPermissions = new HashMap<>();

            Context applicationContext = (Context) objects[1];
            pm = applicationContext.getPackageManager();
            final List<PackageInfo> appInstall= pm.getInstalledPackages(PackageManager.GET_PERMISSIONS|PackageManager.GET_RECEIVERS|
                    PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS);

            for(PackageInfo pInfo:appInstall) {
                String[] reqPermission = pInfo.requestedPermissions;
                ArrayList<String> permissions = new ArrayList<>();
                if (reqPermission != null){
                    permissions.addAll(Arrays.asList(reqPermission));
                }
                appPermissions.put(pInfo.applicationInfo.loadLabel(pm).toString(), permissions);
            }
            return appPermissions;
        }

        private Boolean insertAppsAndPermissions(HashMap<String, ArrayList> stringArrayListHashMap) {
            //creates document
            Document document = new Document();
            //getting destination
            File path = mContextApps.getFilesDir();
            File file = new File(path, Constants.INSTALLED_APPS_NAME);
            // Location to save
            PdfWriter writer = null;
            try {
                writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            } catch (DocumentException e) {
                Log.e("Main", "document exception: " + e);
            } catch (FileNotFoundException e) {
                Log.e("Main", "File not found exception: " + e);
            }
            try {
                if (writer != null) {
                    writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
                }
            } catch (DocumentException e) {
                Log.e("Main", "document exception: " + e);
            }
            if (writer != null) {
                writer.createXmpMetadata();
            }
            // Open to write
            document.open();

            PdfPTable table = new PdfPTable(1);
            //attempts to add the columns
            try {
                int count = 0;
                for (Map.Entry<String, ArrayList> item : stringArrayListHashMap.entrySet()) {
                    String key = item.getKey();
                    pushAppIntoPrefs(key);
                    table.addCell("£$$"+key);
                    ArrayList value = item.getValue();
                    for (int i = 0; i < value.size(); i++){
                        table.addCell("$££"+value.get(i));
                    }
                    count++;
                    int currentProgress = (count * 100) / stringArrayListHashMap.size();
                    publishProgress(currentProgress);
                }

            } catch (Exception e) {
                Log.e("file construct", "error " + e);
            }

            //add to document
            document.setPageSize(PageSize.A4);
            document.addCreationDate();
            try {
                document.add(table);
            } catch (DocumentException e) {
                Log.e("App reader", "Document exception: " + e);
            }
            document.addAuthor("Kris");
            document.close();

            String directory = (String.valueOf(mContextApps.getFilesDir()) + File.separator);
            File backgroundLogging = new File(directory + File.separator + Constants.INSTALLED_APPS_NAME);
            Log.i("prefs", String.valueOf(appPrefs.getInt("number of apps",0)));
            return backgroundLogging.exists();
        }

        private void pushAppIntoPrefs(String key) {
            appEditor.putString("app" + appPrefs.getInt("number of apps", 0), key)
                    .putInt("number of apps",(appPrefs.getInt("number of apps", 0)+1)).apply();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            Log.i("Main", "Progress update: " + values[0]);
            progressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            delegate.processFinish(aBoolean);
        }
    }

    @Override
    public void processFinish(Boolean output) {
        if(output){
            editor.putBoolean("context provided", true).apply();
            progressBar.setProgress(0);
            directNextStageOfInitialization(detectState());
        }else{{
            notifyOfProblemWithDocumentingInstalledApps();
        }}

    }

    private void notifyOfProblemWithDocumentingInstalledApps() {
        StringBuilder toRelayToParticipant = new StringBuilder();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        toRelayToParticipant.append("There was some problem with documenting the apps that are installed on your phone, please inform the researcher").append("\n");
        builder.setTitle("usage app")
                .setMessage(toRelayToParticipant)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putBoolean("instructions shown", true)
                                .apply();
                        directNextStageOfInitialization(detectState());
                    }
                });
        builder.create()
                .show();
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

    //detects a response to the activity
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

    private void documentUsageRetroactively() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            @SuppressLint("WrongConstant") UsageStatsManager usmLLL = (UsageStatsManager) this.getSystemService("usagestats");
            documentPreviousUsage DPU = new documentPreviousUsage(this);
            DPU.execute(usmLLL, this, getApplicationContext(),getBaseContext());
        }else{
            Toast.makeText(this, R.string.too_old, Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    class documentPreviousUsage extends AsyncTask<Object, Integer, Boolean>{

        int numTaskComplete;

        Context context;
        Context applicationContext;
        Context baseContext;
        HashMap<String , Integer> uninstalledApps;
        UsageStatsManager usm;
        PackageManager pm;
        long startDate;


        String TAG;

        AsyncResponse delegate = null;

        documentPreviousUsage(AsyncResponse delegate) {
            this.delegate = delegate;
        }


        @Override
        protected Boolean doInBackground(Object... objects) {
            numTaskComplete = 0;
            TAG = "DPU";
            Log.i(TAG, "upload history operations called");

            context = (Context) objects[1];
            applicationContext = (Context) objects[2];
            baseContext = (Context) objects[3];

            uninstalledApps = new HashMap<>();
            usm = (UsageStatsManager) objects[0];
            pm = applicationContext.getPackageManager();

            documentStatistic(recordUsageStatistics());
            documentEvents(recordUsageEvent());
            return true;
        }


    private void documentStatistic(HashMap<Long, String> stats) {

        //creates document
        Document document = new Document();
        //getting destination
        File path = context.getFilesDir();
        File file = new File(path, Constants.PAST_USAGE_NAME);
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();

        PdfPTable table = new PdfPTable(2);
        //attempts to add the columns
        table.addCell("start date");
        table.addCell(String.valueOf(startDate));
        try {
            int count = 0;
            for (Map.Entry<Long, String> app : stats.entrySet()) {
                table.addCell("££$" + app.getValue());
                table.addCell( "$$£"+ String.valueOf(app.getKey()));
                int currentProgress = (count * 100) / stats.size();
                count++;
                publishProgress(currentProgress);
            }
        }catch (Exception e) {
            Log.e("file construct", "error " + e);
        }

        //add to document
        document.setPageSize(PageSize.A4);
        document.addCreationDate();
        try {
            document.add(table);
        } catch (DocumentException e) {
            Log.e(TAG, "Document exception: " + e);
        }
        document.addAuthor("Kris");
        document.close();
        numTaskComplete++;

    }

    private HashMap<Long, String> recordUsageStatistics() {
        @SuppressLint("WrongConstant") UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService("usagestats");

        List<UsageStats> appList;
        Calendar startFrom = Calendar.getInstance();
        startFrom.add(Calendar.DAY_OF_YEAR, -researcherInput.NUMBER_OF_DAYS_BACK_TO_MEASURE_DURATION_OF_USE);
        long start = startFrom.getTimeInMillis();
        startFrom.add(Calendar.DAY_OF_YEAR, researcherInput.DURATION_OF_BINS_OF_DURATION_IN_DAYS);
        long endOfBracket = startFrom.getTimeInMillis();
        HashMap<Long, String> mySortedMap = new HashMap<>();
        while(start < System.currentTimeMillis()){
            if (usageStatsManager != null) {
                if (endOfBracket > System.currentTimeMillis()) {
                    appList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis());
                } else {
                    appList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, endOfBracket, System.currentTimeMillis());
                }
            if(appList != null && appList.size() > 0){
                long totalUsage = 0;
                for(UsageStats usageStats : appList){
                    totalUsage+=usageStats.getTotalTimeInForeground();
                    mySortedMap.put(usageStats.getTotalTimeInForeground(), usageStats.getPackageName());
                    if(usageStats.getFirstTimeStamp() < startDate){
                        startDate = usageStats.getFirstTimeStamp();
                    }
                }
                if(endOfBracket>System.currentTimeMillis()) {
                    mySortedMap.put(totalUsage, start + " - " + endOfBracket);
                }else{
                    mySortedMap.put(totalUsage, start + " - " + System.currentTimeMillis());
                }
            }

            //finish with
            start = startFrom.getTimeInMillis();
            startFrom.add(Calendar.DAY_OF_YEAR, researcherInput.DURATION_OF_BINS_OF_DURATION_IN_DAYS);
            endOfBracket = startFrom.getTimeInMillis();
            }

        startDate = start;
        }
        Log.i("start date", ""+startDate);

        return mySortedMap;

    }

        private HashMap<Long, HashMap<String, Integer>> recordUsageEvent() {
            HashMap<Long, HashMap<String, Integer>>CompleteRecord = new HashMap<>();
            UsageEvents usageEvents = null;

            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, -researcherInput.TIMESAMPLED_FOR_EVENTS_OF_USAGE);
            long start = calendar.getTimeInMillis();

            if (usm != null) {
                usageEvents =  usm.queryEvents(start, System.currentTimeMillis());
            }else{
                Log.e(TAG, "usm equals null");
            }

            ArrayList <String> databaseEvent = new ArrayList<>();
            ArrayList<Long>databaseTimestamp = new ArrayList<>();
            ArrayList <Integer> databaseEventType = new ArrayList<>();

            int count = 0;
            if (usageEvents != null) {
                while(usageEvents.hasNextEvent()){
                    //Log.i(TAG, "number: " + count);
                    UsageEvents.Event e = new UsageEvents.Event();
                    usageEvents.getNextEvent(e);
                    count++;
                }

                usageEvents =  usm.queryEvents(start, System.currentTimeMillis());


                int newCount = 0;
                while(usageEvents.hasNextEvent()){

                    UsageEvents.Event e = new UsageEvents.Event();
                    usageEvents.getNextEvent(e);

                    try {
                        String appName = (String) pm.getApplicationLabel(pm.getApplicationInfo(e.getPackageName(), PackageManager.GET_META_DATA));
                        appName = appName.replace(" ", "-");
                        databaseEvent.add(appName);
                        //Log.i("app name", appName );
                    } catch (PackageManager.NameNotFoundException e1) {

                        String packageName = e.getPackageName();
                        packageName = packageName.replace(" ", "-");
                        if(uninstalledApps.containsKey(packageName)){
                            databaseEvent.add("app" +uninstalledApps.get(packageName));
                        }else{
                            databaseEvent.add("app"+uninstalledApps.size());
                            uninstalledApps.put(packageName, uninstalledApps.size());
                        }
                        Log.e("usageHistory","Error in identify package name: " + e);
                    }

                    databaseTimestamp.add(e.getTimeStamp());
                    databaseEventType.add(e.getEventType());

                    newCount++;
                    publishProgress((newCount*100)/count);
                    if(newCount%100==0){
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }


                    for (int i = 0; i <databaseEvent.size(); i ++){
                        HashMap<String, Integer>eventAndEventType = new HashMap<>();
                        eventAndEventType .put(databaseEvent.get(i), databaseEventType.get(i));
                        CompleteRecord.put(databaseTimestamp.get(i), eventAndEventType);
                    }
                }

            }else{
                Log.e(TAG, "usage event equals null");
            }
            return CompleteRecord;
        }

    private void documentEvents(HashMap<Long, HashMap<String, Integer>> stringHashMapHashMap) {
        //creates document
        Document document = new Document();
        //getting destination
        File path = context.getFilesDir();
        File file = new File(path, Constants.PAST_EVENTS_NAME);
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();

        PdfPTable table = new PdfPTable(3);
        //attempts to add the columns
        try {
            int rowSize = stringHashMapHashMap.size();
            int currentRow = 0;
            for (Long key: stringHashMapHashMap.keySet()){
                HashMap<String, Integer> toStore = stringHashMapHashMap.get(key);
                //timestamp
                table.addCell("@€£" + key);

                for (String nestedKey : toStore.keySet()){
                    //database event
                    table.addCell("#£$" + nestedKey);
                    //database type
                    table.addCell("^&*" + toStore.get(nestedKey));
                }
                if(currentRow %10 ==0){
                    int currentProgress = (currentRow * 100) / rowSize;
                    publishProgress(currentProgress);
                }

            }
        } catch (Exception e) {
            Log.e("file construct", "error " + e);
        }

        //add to document
        document.setPageSize(PageSize.A4);
        document.addCreationDate();
        try {
            document.add(table);
        } catch (DocumentException e) {
            Log.e(TAG, "Document exception: " + e);
        }
        document.addAuthor("Kris");
        document.close();
        numTaskComplete++;
    }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            delegate.processFinish(aBoolean);
        }

    }

    /**
     * --------------------------Need to determine the UI effectively here--------------------------
     */

    private void determineUI() {
    }

    private void initializeButtons() {
        Button emailBtn = findViewById(R.id.btnEmail);
        emailBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnEmail:
                packageFiles pf = new packageFiles();
                pf.execute(this);
                break;
        }
    }

     //When the app is closed, the listening for the broadcasts from the service is stopped.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(serviceListener);
    }

    private void initializeVisualComponents() {
        statusTV = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.pb);
        progressBar.setProgress(0);
    }

    //make async task where the SQL files are packaged into Pdf files and puts them in an email

    class packageFiles extends AsyncTask<Object, Integer, Void>{

        Context mContext;

        @Override
        protected Void doInBackground(Object[] objects) {
            initializeComponents(objects);
            switch(establishDataToAccess()){
                case Constants.LOGGING_BASIC:
                    packageBackgroundLoggingSQL();
                    break;
                case Constants.LOGGING_FOREGROUND:

                    break;
                case Constants.LOGGING_NOTIFICATION:

                    break;
                case Constants.LOGGING_FOREGROUND_AND_NOTIFICATION:

                    break;
            }
            return null;
        }

        private void initializeComponents(Object[] objects) {
            mContext = (Context) objects[0];
        }

        private void packageBackgroundLoggingSQL() {
            //creates document
            Document document = new Document();
            //getting destination
            File path = mContext.getFilesDir();
            File file = new File(path, Constants.BACKGROUND_LOGGING_NAME);
            // Location to save
            PdfWriter writer = null;
            try {
                writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            } catch (DocumentException e) {
                Log.e("MAIN", "document exception: " + e);
            } catch (FileNotFoundException e) {
                Log.e("MAIN", "File not found exception: " + e);
            }
            try {
                if (writer != null) {
                    writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
                }
            } catch (DocumentException e) {
                Log.e("MAIN", "document exception: " + e);
            }
            if (writer != null) {
                writer.createXmpMetadata();
            }
            // Open to write
            document.open();


            String selectQuery = "SELECT * FROM " + BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME;
            SQLiteDatabase db = BackgroundLoggingSQL.getInstance(mContext).getReadableDatabase(prefs.getString("password", "not to be used"));

            Cursor c = db.rawQuery(selectQuery, null);

            int event = c.getColumnIndex(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.EVENT);
            int time = c.getColumnIndex(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TIME);



            PdfPTable table = new PdfPTable(2);
            //attempts to add the columns
            c.moveToLast();
            int rowLength =  c.getCount();
            if(rowLength > 0){
                try {
                    for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                        table.addCell(c.getString(event));
                        table.addCell(String.valueOf(c.getLong(time)));
                        if (c.getCount() != 0) {
                            int currentProgress = (c.getCount() * 100) / rowLength;
                            publishProgress(currentProgress);
                        }
                    }
                } catch (Exception e) {
                    Log.e("file construct", "error " + e);
                }

                c.close();
                db.close();


                //add to document
                document.setPageSize(PageSize.A4);
                document.addCreationDate();
                try {
                    document.add(table);
                } catch (DocumentException e) {
                    Log.e("MAIN", "Document exception: " + e);
                }
                document.addAuthor("Kris");
                document.close();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            Log.i("Main", "Progress update: " + values[0]);
            progressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.i("MAIN", "Email started");
            //documenting intent to send multiple
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("text/plain");

            //getting directory for internal files
            String directory = (String.valueOf(mContext.getFilesDir()) + File.separator);
            Log.i("Directory", directory);

            //initializing files reference
            File backgroundLogging = new File(directory + File.separator + Constants.BACKGROUND_LOGGING_NAME),

            appDocumented = new File(directory + File.separator + Constants.INSTALLED_APPS_NAME);


            File pastUsage = new File(directory + File.separator + Constants.PAST_USAGE_NAME);

            File pastEvent = new File(directory + File.separator + Constants.PAST_EVENTS_NAME);

            //list of files to be uploaded
            ArrayList<Uri> files = new ArrayList<>();

            //if target files are identified to exist then they are packages into the attachments of the email
            try {
                if(backgroundLogging.exists()){
                    files.add(FileProvider.getUriForFile(mContext, "architecture.geyerk.sensorlab.usagearchitecture.fileprovider", backgroundLogging));
                }

                if(appDocumented.exists()){
                    files.add(FileProvider.getUriForFile(mContext, "architecture.geyerk.sensorlab.usagearchitecture.fileprovider", appDocumented));
                }

                if(pastUsage.exists()){
                    files.add(FileProvider.getUriForFile(mContext, "architecture.geyerk.sensorlab.usagearchitecture.fileprovider", pastUsage));
                }

                if(pastEvent.exists()){
                    files.add(FileProvider.getUriForFile(mContext, "architecture.geyerk.sensorlab.usagearchitecture.fileprovider", pastEvent));
                }

                //adds the file to the intent to send multiple data points
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mContext.startActivity(intent);
            }
            catch (Exception e){
                Log.e("File upload error1", "Error:" + e);
            }

        }
        }

}

