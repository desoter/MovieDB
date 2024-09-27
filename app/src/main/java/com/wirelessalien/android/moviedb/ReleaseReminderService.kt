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
package com.wirelessalien.android.moviedb

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReleaseReminderService(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        val databaseHelper = MovieDatabaseHelper(applicationContext)
        val db = databaseHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM " + MovieDatabaseHelper.TABLE_MOVIES, null)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val currentDate = sdf.format(Date())
        while (cursor.moveToNext()) {
            val title =
                cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE))
            val releaseDate =
                cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE))
            if (releaseDate != null && releaseDate == currentDate) {
                createNotification(title)
            }
        }
        cursor.close()
        val episodeDatabaseHelper = EpisodeReminderDatabaseHelper(applicationContext)
        val dbEpisode = episodeDatabaseHelper.readableDatabase
        val cursorEpisode = dbEpisode.rawQuery(
            "SELECT * FROM " + EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
            null
        )
        while (cursorEpisode.moveToNext()) {
            val tvShowName = cursorEpisode.getString(
                cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME)
            )
            val episodeName = cursorEpisode.getString(
                cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_NAME)
            )
            val episodeNumber = cursorEpisode.getString(
                cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER)
            )
            val airDate = cursorEpisode.getString(
                cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_DATE)
            )
            if (airDate != null && airDate == currentDate) {
                createEpisodeNotification(tvShowName, episodeName, episodeNumber)
            }
        }
        cursorEpisode.close()
        val workRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(ReleaseReminderService::class.java)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        return Result.success()
    }

    private fun createNotification(title: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )
        val shouldNotify = sharedPreferences.getBoolean(NOTIFICATION_PREFERENCES, true)
        if (shouldNotify) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.putExtra("tab_index", 2)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(applicationContext, "released_movies")
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(applicationContext.getString(R.string.movie_released_today, title))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notificationManager.notify(notificationIdMovie, builder.build())
        }
    }

    private fun createEpisodeNotification(
        tvShowName: String,
        episodeName: String,
        episodeNumber: String
    ) {
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.putExtra("tab_index", 2)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(applicationContext, "episode_reminders")
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(tvShowName)
            .setContentText(
                applicationContext.getString(
                    R.string.episode_airing_today,
                    episodeNumber,
                    episodeName
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(notificationIdEpisode, builder.build())
    }

    companion object {
        private const val notificationIdMovie = 1
        private const val notificationIdEpisode = 2
        private const val NOTIFICATION_PREFERENCES = "key_get_notified_for_saved"
    }
}