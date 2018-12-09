package architecture.geyerk.sensorlab.usagearchitecture;

import android.content.Context;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

public class BackgroundLoggingSQL extends SQLiteOpenHelper {


    private static BackgroundLoggingSQL instance;
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "backgroundLogger.db",
    SQL_CREATE_ENTRIES =
            "CREATE TABLE " + BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME + " (" +
                    BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.COLUMN_NAME_ENTRY + " INTEGER PRIMARY KEY AUTOINCREMENT,"+
                    BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.EVENT + " TEXT," +
                    BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TIME + " INTEGER" + " )",
    SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME;


    public BackgroundLoggingSQL(Context context){
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    static public synchronized BackgroundLoggingSQL getInstance(Context context){
        if(instance == null) {
            instance = new BackgroundLoggingSQL(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }
}
