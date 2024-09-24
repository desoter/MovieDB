/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.fragment

import android.content.ContentValues
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter
import com.wirelessalien.android.moviedb.data.Episode
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.ParseException
import java.util.Collections
import java.util.Date
import java.util.Locale

class SeasonDetailsFragment : Fragment() {
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewPager: ViewPager2
    private var tvShowId = 0
    private var seasonNumber = 0
    private var currentTabNumber = 1
    private lateinit var pageChangeCallback: ViewPager2.OnPageChangeCallback
    private var dbHelper: EpisodeReminderDatabaseHelper? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tv_season_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
        seasonNumber = requireArguments().getInt(ARG_SEASON_NUMBER)
        rvEpisodes = view.findViewById(R.id.episodeRecyclerView)
        toolbar = view.findViewById(R.id.toolbar)
        val appBarLayout = view.findViewById<AppBarLayout>(R.id.appBarLayout)
        appBarLayout.setBackgroundColor(Color.TRANSPARENT)
        viewPager = requireActivity().findViewById(R.id.view_pager)
        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentTabNumber = position + 1
                requireActivity().invalidateOptionsMenu()
            }
        }
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            progressBar.visibility = View.VISIBLE
            try {
                val tvSeasonDetails = TVSeasonDetails(tvShowId, seasonNumber, requireContext())
                tvSeasonDetails.fetchSeasonDetails(object : TVSeasonDetails.SeasonDetailsCallback {
                    override fun onSeasonDetailsFetched(episodes: List<Episode>) {
                        val adapter = EpisodeAdapter(requireContext(), episodes, currentTabNumber, tvShowId)
                        rvEpisodes.layoutManager = LinearLayoutManager(requireContext())
                        rvEpisodes.adapter = adapter
                        progressBar.visibility = View.GONE
                        requireActivity().invalidateOptionsMenu()
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.notification_menu, menu)
        dbHelper = EpisodeReminderDatabaseHelper(requireContext())
        val notificationItem = menu.findItem(R.id.action_notification)
        val tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
        if (isShowInDatabase(tvShowId)) {
            notificationItem.setIcon(R.drawable.ic_notifications_active)
        } else {
            notificationItem.setIcon(R.drawable.ic_add_alert)
        }
        val adapter = rvEpisodes.adapter as EpisodeAdapter?
        if (adapter != null) {
            val episodes = adapter.episodes
            if (episodes != null) {
                if (episodes.isNotEmpty()) {
                    val latestEpisode = Collections.max(
                        episodes,
                        Comparator.comparingInt { obj: Episode -> obj.episodeNumber })

                    // Parse the air date of the latest episode
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    try {
                        val latestEpisodeDate = sdf.parse(latestEpisode.airDate)
                        val currentDate = Date()

                        // If the air date of the latest episode is older than the current date, disable the notification action
                        if (latestEpisodeDate != null && latestEpisodeDate.before(currentDate)) {
                            notificationItem.setEnabled(false)
                        }
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val watchedItem = menu.findItem(R.id.action_watched)
        val adapter = rvEpisodes.adapter as EpisodeAdapter?
        if (adapter != null) {
            val episodes = adapter.episodes
            val db = MovieDatabaseHelper(requireContext())
            var allEpisodesInDatabase = true
            if (episodes != null) {
                for (episode in episodes) {
                    val seasonEpisodeNumbers: MutableMap<Int, List<Int>> = HashMap()
                    seasonEpisodeNumbers[currentTabNumber] = listOf(episode.episodeNumber)
                    if (!db.isEpisodeInDatabase(
                            tvShowId,
                            currentTabNumber,
                            listOf(episode.episodeNumber)
                        )
                    ) {
                        allEpisodesInDatabase = false
                        break
                    }
                }
            }
            if (allEpisodesInDatabase) {
                watchedItem.setIcon(R.drawable.ic_visibility_fill)
            } else {
                watchedItem.setIcon(R.drawable.ic_visibility)
            }
        }
    }

    private fun isShowInDatabase(tvShowId: Int): Boolean {
        val db = dbHelper!!.readableDatabase
        val selection = EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID + " = ?"
        val selectionArgs = arrayOf(tvShowId.toString())
        val cursor = db.query(
            EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
            null, selection, selectionArgs, null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_notification) {
            dbHelper = EpisodeReminderDatabaseHelper(requireContext())
            if (isShowInDatabase(requireArguments().getInt(ARG_TV_SHOW_ID))) {
                dbHelper!!.deleteData(requireArguments().getInt(ARG_TV_SHOW_ID))
                val message = getString(R.string.removed_from_reminder, ARG_TV_SHOW_NAME)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                item.setIcon(R.drawable.ic_add_alert)
                return true
            }
            val db = dbHelper!!.writableDatabase
            val adapter = rvEpisodes.adapter as EpisodeAdapter?
            if (adapter != null) {
                for (episode in adapter.episodes!!) {
                    val values = ContentValues()
                    val tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID, tvShowId)
                    val tvShowName = requireArguments().getString(ARG_TV_SHOW_NAME)
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME, tvShowName)
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_NAME, episode.name)
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_DATE, episode.airDate)
                    values.put(
                        EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER,
                        episode.episodeNumber
                    )
                    val newRowId = db.insert(
                        EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
                        null,
                        values
                    )
                    if (newRowId == -1L) {
                        val message = getString(R.string.error_reminder_episode, tvShowName)
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
                val message = getString(
                    R.string.get_notified_for_episode, requireArguments().getString(
                        ARG_TV_SHOW_NAME
                    )
                )
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                item.setIcon(R.drawable.ic_notifications_active)
                return true
            }
            super.onOptionsItemSelected(item)
        } else if (item.itemId == R.id.action_watched) {
            val adapter = rvEpisodes.adapter as EpisodeAdapter?
            if (adapter != null) {
                val episodes = adapter.episodes
                val db = MovieDatabaseHelper(requireContext())
                var allEpisodesInDatabase = true
                if (episodes != null) {
                    for (episode in episodes) {
                        if (!db.isEpisodeInDatabase(
                                tvShowId,
                                currentTabNumber,
                                listOf(episode.episodeNumber)
                            )
                        ) {
                            allEpisodesInDatabase = false
                            break
                        }
                    }
                }
                if (!allEpisodesInDatabase) {
                    if (episodes != null) {
                        for (episode in episodes) {
                            if (!db.isEpisodeInDatabase(
                                    tvShowId,
                                    currentTabNumber,
                                    listOf(episode.episodeNumber)
                                )
                            ) {
                                db.addEpisodeNumber(
                                    tvShowId,
                                    currentTabNumber,
                                    listOf(episode.episodeNumber)
                                )
                            }
                        }
                    }
                    item.setIcon(R.drawable.ic_visibility_fill)
                    Toast.makeText(requireContext(), R.string.episodes_removed, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    if (episodes != null) {
                        for (episode in episodes) {
                            db.removeEpisodeNumber(
                                tvShowId,
                                currentTabNumber,
                                listOf(episode.episodeNumber)
                            )
                        }
                    }
                    item.setIcon(R.drawable.ic_visibility)
                    Toast.makeText(requireContext(), R.string.episodes_added, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val ARG_TV_SHOW_ID = "tvShowId"
        private const val ARG_SEASON_NUMBER = "seasonNumber"
        private const val ARG_TV_SHOW_NAME = "tvShowName"
        @JvmStatic
        fun newInstance(
            tvShowId: Int,
            seasonNumber: Int,
            tvShowName: String?
        ): SeasonDetailsFragment {
            val fragment = SeasonDetailsFragment()
            val args = Bundle()
            args.putInt(ARG_TV_SHOW_ID, tvShowId)
            args.putInt(ARG_SEASON_NUMBER, seasonNumber)
            args.putString(ARG_TV_SHOW_NAME, tvShowName)
            fragment.arguments = args
            return fragment
        }
    }
}