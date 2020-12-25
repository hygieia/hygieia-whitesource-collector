package com.capitalone.dashboard.utils;

import com.capitalone.dashboard.misc.HygieiaException;
import org.apache.commons.lang3.StringUtils;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.capitalone.dashboard.utils.Constants.yyyy_MM_dd_HH_mm_ss;
import static com.capitalone.dashboard.utils.Constants.yyyy_MM_dd_HH_mm_ss_z;

/**
 * Whitesource date time formats in different apis response
 * In Change Log: "startDateTime": "2020-12-18 19:21:33",
 * In Project Vitals:
 *              "creationDate": "2020-12-21 15:37:48 +0000",
 *             "lastUpdatedDate": "2020-12-21 16:45:11 +0000"
 * In alerts:
 *             "date": "2020-12-21",
 *             "modifiedDate": "2020-12-21",
 *             "time": 1608559754000,
 *             "creation_date": "2020-12-21",
 *
 *
 * Whitesource date time formats in different apis request
 * {
 *     "requestType" : "getOrganizationAlertsByType",
 *     "userKey": "user_key",
 *     "alertType" : "alert_type",
 *     "orgToken" : "organization_api_key",
 *     "fromDate" : "2016-01-01 10:00:00",
 *     "toDate" : "2016-01-02 10:00:00"
 * }
 *
 */
public class DateTimeUtils {

    /**
     * Calculates timestamp in milliseconds for a String Date time with specified format and timezone
     * @param fromDateTime String date time
     * @param fromTimeZone Time zone of the input date time
     * @param fromPattern Pattern for the input date time
     * @return time in milliseconds
     * @throws HygieiaException
     */
    public static long timeFromStringToMillis (String fromDateTime, String fromTimeZone, String fromPattern) throws HygieiaException {
        if (StringUtils.isNotEmpty(fromDateTime)) {
            return DateTimeFormat.forPattern(fromPattern).withZone(DateTimeZone.forID(fromTimeZone)).parseMillis(fromDateTime);
        } else {
            throw new HygieiaException("Date Time value cannot be empty", HygieiaException.BAD_DATA);
        }
    }


    /**
     * Calculates String date time for a given toPattern format and toTimeZone zone.
     * @param fromTimeStamp milliseconds time to be converted
     * @param toTimeZone target time zone
     * @param toPattern target pattern
     * @return String formatted date time
     */
    public static String timeFromLongToString (long fromTimeStamp, String toTimeZone, String toPattern) {
        DateTime dateTime = new DateTime(fromTimeStamp, DateTimeZone.forID(toTimeZone));
        DateTimeFormatter formatter = DateTimeFormat.forPattern(toPattern);
        return dateTime.toString(formatter);
    }


    /**
     * Calculates number of days from the given date
     * @param fromDateTime from the date time
     * @param fromDateTimePattern from date format
     * @param fromDateTimeZone from date timezone
     * @return number of days
     * @throws HygieiaException Hygieia Exception
     */
    public static long getDays(String fromDateTime, String fromDateTimePattern, String fromDateTimeZone) throws HygieiaException {
        long timestamp = timeFromStringToMillis(fromDateTime, fromDateTimeZone, fromDateTimePattern);
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp);
    }

    /**
     * Calculates number of days from the given date time in millis
     * @param fromTimeMillis from the date time
     * @return number of days
     */
    public static long getDays(long fromTimeMillis) {
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - fromTimeMillis);
    }

    public static void main(String[] args) {
        String date = "2020-12-21 15:37:48 +0000";
        try {
            long time = timeFromStringToMillis(date,"UTC", yyyy_MM_dd_HH_mm_ss_z);
            System.out.println("done");
        } catch (HygieiaException e) {
            e.printStackTrace();
        }

    }
}
