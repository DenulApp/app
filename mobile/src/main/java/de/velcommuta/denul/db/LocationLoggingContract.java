package de.velcommuta.denul.db;

import android.provider.BaseColumns;

/**
 * SQL Contract class for the location tracking database, as proposed in
 * https://developer.android.com/training/basics/data-storage/databases.html
 */
public class LocationLoggingContract  {
    /**
     * Empty constructor (unused, as this is a container for constant values)
     */
    public LocationLoggingContract() {}

    public static abstract class LocationLog implements BaseColumns {
        // Name of the SQLite Table to be created
        public static final String TABLE_NAME = "location_log";

        // Session this coordinate belongs to
        public static final String COLUMN_NAME_SESSION = "session";

        // Timestamp when the coordinates were taken
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";

        // GPS Latitude and Longitude
        public static final String COLUMN_NAME_LAT = "latitude";
        public static final String COLUMN_NAME_LONG = "longitude";
    }

    public static abstract class LocationSessions implements BaseColumns {
        // Name of the SQLite Table to be created
        public static final String TABLE_NAME = "location_session";

        // Session Name
        public static final String COLUMN_NAME_NAME = "name";
        // Owner of the session - References Friend ID, or set to -1 if user is owner.
        public static final String COLUMN_NAME_OWNER = "owner";
        public static final String COLUMN_NAME_SHARE_ID = "share_id";

        // Session start and end timestamp
        public static final String COLUMN_NAME_SESSION_START = "session_start";
        public static final String COLUMN_NAME_SESSION_END = "session_end";
        public static final String COLUMN_NAME_TIMEZONE = "timezone";

        // Distance
        public static final String COLUMN_NAME_DISTANCE = "distance";

        // Mode of transportation
        public static final String COLUMN_NAME_MODE = "modeoftransport";

        // Description
        public static final String COLUMN_NAME_DESCRIPTION = "description";
    }
}
