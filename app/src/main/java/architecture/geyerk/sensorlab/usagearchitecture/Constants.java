package architecture.geyerk.sensorlab.usagearchitecture;

public final class Constants {


    static final int ALL_PERMISSIONS_GIVEN = 100,
     NOTIFICATIONS_PERMISSION_REQUIRED = 101,
     USAGE_STATISTICS_PERMISSION_REQUIRED = 102,
     MULTIPLE_PERMISSIONS_REQUIRED = 103,

     NOTIFICATIONS_PERMISSION_GRANTED = 104,
     NOTIFICATIONS_PERMISSION_DENIED = 105,
     USAGE_STATISTICS_PERMISSION_GRANTED = 106,
     USAGE_STATISTICS_PERMISSION_DENIED = 107,

     REQUEST_USAGE_STATISTICS = 110,
     REQUEST_NOTIFICATION_LISTENER = 111,

     SHOW_PRIVACY_POLICY = 120,

     LOGGING_BASIC = 130,
     LOGGING_FOREGROUND = 131,
     LOGGING_NOTIFICATION = 132,
     LOGGING_FOREGROUND_AND_NOTIFICATION = 133;

    static final String BACKGROUND_LOGGING_NAME = "backgroundLogging.pdf",
                        INSTALLED_APPS_NAME = "installedApps.pdf",
                        PAST_USAGE_NAME = "pastUsageRecords.pdf",
                        PAST_EVENTS_NAME = "pastEvent.pdf";
}
