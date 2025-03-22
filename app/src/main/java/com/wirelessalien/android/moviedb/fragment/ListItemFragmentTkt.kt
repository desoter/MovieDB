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

package com.wirelessalien.android.moviedb.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowTraktAdapter
import com.wirelessalien.android.moviedb.databinding.FragmentWatchlistTktBinding
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ListItemFragmentTkt : BaseFragment() {

    private lateinit var adapter: ShowTraktAdapter
    private val listItemList = ArrayList<JSONObject>()
    private val fullListItemList = ArrayList<JSONObject>()
    private lateinit var dbHelper: TraktDatabaseHelper
    private lateinit var tmdbHelper: TmdbDetailsDatabaseHelper
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var listId: Int? = null
    private lateinit var binding: FragmentWatchlistTktBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            listId = it.getInt("id")
        }
        dbHelper = TraktDatabaseHelper(requireContext())
        tmdbHelper = TmdbDetailsDatabaseHelper(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWatchlistTktBinding.inflate(inflater, container, false)
        val view = binding.root

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {

            if (binding.showRecyclerView.layoutManager !is GridLayoutManager) {
                adapter = ShowTraktAdapter(listItemList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
            }

            val mShowGridView = GridLayoutManager(requireActivity(), preferences.getInt(GRID_SIZE_PREFERENCE, 3))
            binding.showRecyclerView.layoutManager = mShowGridView
            linearLayoutManager = mShowGridView
        } else {

            if (binding.showRecyclerView.layoutManager !is LinearLayoutManager) {
                adapter = ShowTraktAdapter(listItemList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
            }
            linearLayoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
            binding.showRecyclerView.layoutManager = linearLayoutManager
        }

        binding.showRecyclerView.adapter = adapter

        binding.chipAll.isChecked = true
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipAll -> adapter.updateShowList(fullListItemList)
                    R.id.chipMovie -> filterListItemData("movie")
                    R.id.chipShow -> filterListItemData("show")
                    R.id.chipSeason -> filterListItemData("season")
                    R.id.chipEpisode -> filterListItemData("episode")
                }
            }
        }

        loadListItemData()
        return view
    }

    private fun loadListItemData() {
        lifecycleScope.launch {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            val newList = withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val tmdbDb = tmdbHelper.readableDatabase

                val cursor = db.query(
                    TraktDatabaseHelper.TABLE_LIST_ITEM,
                    null,
                    "${TraktDatabaseHelper.COL_TRAKT_ID} = ?",
                    arrayOf(listId.toString()),
                    null,
                    null,
                    null
                )

                val tempList = ArrayList<JSONObject>()
                if (cursor.moveToFirst()) {
                    do {
                        val jsonObject = JSONObject().apply {
                            put("auto_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_ID)))
                            put("listed_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_LISTED_AT)))
                            put("type", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE)))
                            put("title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TITLE)))
                            put("year", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_YEAR)))
                            when (cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE))) {
                                "movie", "show" -> {
                                    put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                                }
                                "episode" -> {
                                    put("episode_trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                                    put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TRAKT_ID)))
                                }
                                "season" -> {
                                    put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TRAKT_ID)))
                                }
                            }
                            put("slug", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SLUG)))
                            put("imdb", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_IMDB)))
                            put("tmdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB)))
                            put("tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TVDB)))
                            put("season", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SEASON)))
                            put("number", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NUMBER)))
                            put("rank", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_RANK)))
                            put("notes", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NOTES)))
                            put("show_title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TITLE)))
                            put("show_year", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_YEAR)))
                            put("show_slug", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_SLUG)))
                            put("show_tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TVDB)))
                            put("show_imdb", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_IMDB)))
                            put("show_tmdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TMDB)))
                        }
                        // Fetch additional details from TMDB table
                        val tmdbId = if (jsonObject.getString("type") == "season" || jsonObject.getString("type") == "episode") {
                            cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TMDB))
                        } else {
                            cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB))
                        }

                        val tmdbType = if (jsonObject.getString("type") == "season" || jsonObject.getString("type") == "episode") {
                            "show"
                        } else {
                            jsonObject.getString("type")
                        }

                        val tmdbCursor = tmdbDb.query(
                            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS, null,
                            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ? AND ${TmdbDetailsDatabaseHelper.COL_TYPE} = ?",
                            arrayOf(
                                tmdbId,
                                tmdbType
                            ),
                            null, null, null
                        )

                        if (tmdbCursor.moveToFirst()) {
                            jsonObject.put("id", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_TMDB_ID)))
                            jsonObject.put("name", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_NAME)))
                            jsonObject.put("backdrop_path", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH)))
                            jsonObject.put("poster_path", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_POSTER_PATH)))
                            jsonObject.put("overview", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_SUMMARY)))
                            jsonObject.put("vote_average", tmdbCursor.getDouble(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE)))
                            jsonObject.put("release_date", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_RELEASE_DATE)))
                            jsonObject.put("genre_ids", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_GENRE_IDS)))
                            jsonObject.put("seasons_episode_show_tmdb", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)))
                        }
                        tmdbCursor.close()

                        tempList.add(jsonObject)
                    } while (cursor.moveToNext())
                }
                cursor.close()
                tempList
            }
            val sortedList = applySorting(newList)
            adapter.updateShowList(sortedList)
            binding.shimmerFrameLayout1.visibility = View.GONE
            binding.shimmerFrameLayout1.stopShimmer()
        }
    }

    private fun applySorting(list: ArrayList<JSONObject>): ArrayList<JSONObject> {
        val criteria = preferences.getString("tkt_sort_criteria", "title")
        val order = preferences.getString("tkt_sort_order", "asc")

        val comparator = when (criteria) {
            "title" -> compareBy<JSONObject> { it.optString("title", "") }
            "listed_at" -> compareBy { it.optString("listed_at", "") }
            else -> compareBy { it.optString("title", "") }
        }

        if (order == "desc") {
            list.sortWith(comparator.reversed())
        } else {
            list.sortWith(comparator)
        }

        return list
    }

    private fun filterListItemData(type: String) {
        val filteredList = ArrayList(fullListItemList.filter { it.getString("type") == type })
        adapter.updateShowList(filteredList)
    }
}