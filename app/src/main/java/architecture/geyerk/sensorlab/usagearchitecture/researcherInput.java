package architecture.geyerk.sensorlab.usagearchitecture;

public class researcherInput {

    /**
     * PROSPECTIVE LOGGING
     */
    static final boolean USAGE_STATISTICS_REQUIRED = true,
            //Note the below permission means that only androids that use SDK version - 5.0 (21) or higher will be able to use the app.
            NOTIFICATION_LISTENER_REQUIRED = true;

    //Milliseconds (hence multiplied by 1000, default is once a second)
    static final int FREQUENCY_THAT_FOREGROUND_APP_IS_SURVEYED = 1 * 1000;

    /**
     * CROSS SECTIONAL LOGGING
     */
    static final boolean PERMISSIONS_TO_BE_DOCUMENTED = true,
            DOCUMENT_INSTALLED_APPS = true;


    /**
     * RETROSPECTIVE LOGGING
     */

    static final boolean RETROACTIVITY_ESTABLISH_DURATION_OF_USE = true,
        RETROACTIVITYLY_ESTABLISH_EVENTS_OF_USAGE = false;

    static final int NUMBER_OF_DAYS_BACK_TO_MEASURE_DURATION_OF_USE = 5,
        DURATION_OF_BINS_OF_DURATION_IN_DAYS = 1,
        TIMESAMPLED_FOR_EVENTS_OF_USAGE = 1;

}
