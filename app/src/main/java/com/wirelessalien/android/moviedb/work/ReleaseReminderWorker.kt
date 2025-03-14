/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.NotificationReceiver
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import java.util.Date
import java.util.Locale

class ReleaseReminderWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val dateOnlyFormat2 = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseDate(dateStr: String): Date? {
        return try {
            // Try parsing full date-time format and convert to local time
            dateFormat.parse(dateStr)?.let { parsedDate ->
                Calendar.getInstance(TimeZone.getDefault()).apply {
                    time = parsedDate
                }.time
            }
        } catch (e: Exception) {
            try {
                // Handle date-only formats: convert to full date format first
                val fullDateStr = convertToFullDateFormat(dateStr)
                fullDateStr?.let {
                    // Parse then convert to local time as above
                    dateFormat.parse(it)?.let { parsed ->
                        Calendar.getInstance(TimeZone.getDefault()).apply {
                            time = parsed
                        }.time
                    }
                }
            } catch (e1: Exception) {
                null
            }
        }
    }

    private fun convertToFullDateFormat(dateStr: String): String? {
        return try {
            val date = dateOnlyFormat2.parse(dateStr)
            date?.let {
                dateFormat.format(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun doWork(): Result {
        return try {
            checkAndScheduleNotifications()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun checkAndScheduleNotifications() {
        val episodeDb = EpisodeReminderDatabaseHelper(applicationContext)
        val movieDb = MovieDatabaseHelper(applicationContext)
        val tktDb = TraktDatabaseHelper(applicationContext)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val episodeQuery = """
            SELECT * FROM ${EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS}
            WHERE ${EpisodeReminderDatabaseHelper.COLUMN_DATE} IS NOT NULL
        """.trimIndent()

        episodeDb.readableDatabase.rawQuery(episodeQuery, null).use { cursor ->
            while (cursor.moveToNext()) {
                val showTraktId = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_SHOW_TRAKT_ID))
                val movieId = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID))
                val seasonNumber = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_SEASON))
                val episodeNumber = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER))
                val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_DATE))

                val shouldSchedule = when {
                    showTraktId != null && checkTraktCalendarExists(tktDb, showTraktId) -> true
                    movieId != null && checkMovieExists(movieDb, movieId) -> true
                    else -> false
                }

                if (shouldSchedule && dateStr != null) {
                    val notificationKey = "notification_${showTraktId ?: movieId}_${seasonNumber ?: 0}_${episodeNumber ?: 0}_$dateStr"
                    if (!hasNotificationBeenScheduled(sharedPreferences, notificationKey)) {
                        scheduleNotification(cursor, dateStr, notificationKey)
                    }
                }
            }
        }
    }

    private fun checkTraktCalendarExists(tktDb: TraktDatabaseHelper, traktId: Int): Boolean {
        return tktDb.readableDatabase.rawQuery(
            "SELECT 1 FROM ${TraktDatabaseHelper.TABLE_CALENDER} WHERE ${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ?",
            arrayOf(traktId.toString())
        ).use { it.count > 0 }
    }

    private fun checkMovieExists(movieDb: MovieDatabaseHelper, movieId: Int): Boolean {
        return movieDb.readableDatabase.rawQuery(
            "SELECT 1 FROM ${MovieDatabaseHelper.TABLE_MOVIES} WHERE ${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ?",
            arrayOf(movieId.toString())
        ).use { it.count > 0 }
    }

    private fun scheduleNotification(cursor: Cursor, dateStr: String, notificationKey: String) {
        try {
            val alarmTime = parseDate(dateStr) ?: return

            // Skip scheduling if the alarm time is in the past
            if (alarmTime.time <= System.currentTimeMillis()) {
                return
            }

            val title = cursor.getString(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME))
            val episodeName = cursor.getString(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_NAME))
            val episodeNumber = cursor.getString(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER))
            val type = cursor.getString(cursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_TYPE))

            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(applicationContext, NotificationReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("episodeName", episodeName)
                putExtra("episodeNumber", episodeNumber)
                putExtra("notificationKey", notificationKey)
                putExtra("type", type)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                notificationKey.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime.time,
                pendingIntent
            )

            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putBoolean(notificationKey, true)
                .apply()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasNotificationBeenScheduled(preferences: SharedPreferences, key: String): Boolean {
        return preferences.getBoolean(key, false)
    }

    private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (isNull(columnIndex)) null else getInt(columnIndex)
    }

    companion object {
        fun scheduleWork(context: Context) {
            val calendarWorkRequest = OneTimeWorkRequest.Builder(
                CalenderAllWorkerTkt::class.java
            ).build()

            val reminderWorkRequest = OneTimeWorkRequest.Builder(
                ReleaseReminderWorker::class.java
            ).build()

            WorkManager.getInstance(context)
                .beginUniqueWork(
                    "CalendarAndReminderWork",
                    ExistingWorkPolicy.REPLACE,
                    calendarWorkRequest
                )
                .then(reminderWorkRequest)
                .enqueue()
        }

        fun scheduleWorkwithoutReleaseReminder(context: Context) {
            val calendarWorkRequest = OneTimeWorkRequest.Builder(
                CalenderAllWorkerTkt::class.java
            ).build()

            WorkManager.getInstance(context)
                .beginUniqueWork(
                    "CalendarAndReminderWork",
                    ExistingWorkPolicy.REPLACE,
                    calendarWorkRequest
                )
                .enqueue()
        }
    }
}