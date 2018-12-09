package architecture.geyerk.sensorlab.usagearchitecture;

import android.provider.BaseColumns;

public class BackgroundLoggingSQLCols {
    public BackgroundLoggingSQLCols(){}

    public static abstract class BackgroundLoggingSQLColsNames implements BaseColumns {
        public static final String  TABLE_NAME = "background_logging";
        public static final String  COLUMN_NAME_ENTRY = "column_id";
        public static final String  EVENT = "event";
        public static final String  TIME = "time";

    }
}
