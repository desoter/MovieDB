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
package com.wirelessalien.android.moviedb.adapter

import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.BottomSheetSeasonEpisodeBinding
import com.wirelessalien.android.moviedb.databinding.ShowCardBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardBinding
import com.wirelessalien.android.moviedb.fragment.ListFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.IS_MOVIE
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.IS_UPCOMING
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShowBaseAdapter(
    val context: Context,
    showList: ArrayList<JSONObject>,
    genreList: HashMap<String, String?>, gridView: Boolean
) : RecyclerView.Adapter<ShowBaseAdapter.ShowItemViewHolder?>() {
    private val mShowArrayList: ArrayList<JSONObject>
    private val mGenreHashMap: HashMap<String, String?>
    private var mGridView: Boolean
    private var genreType: String? = null

    init {
        genreType = when (genreType) {
            null, "2" -> {
                null
            }
            "1" -> {
                SectionsPagerAdapter.MOVIE
            }
            else -> {
                SectionsPagerAdapter.TV
            }
        }
        mShowArrayList = showList
        mGenreHashMap = genreList
        mGridView = gridView
    }

    override fun getItemCount(): Int {
        return mShowArrayList.size
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        return if (mGridView) {
            val gridBinding = ShowGridCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ShowItemViewHolder(null, gridBinding)
        } else {
            val binding = ShowCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ShowItemViewHolder(binding, null)
        }
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData = mShowArrayList[position]
        val context = holder.showView.context

        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            if (showData.getString(KEY_POSTER) == "null") {
                holder.showImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/$imageSize" + showData.getString(KEY_POSTER)).into(holder.showImage)
            }

            val name = if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(KEY_NAME)
            holder.showTitle.text = name

            if (showData.has(KEY_CATEGORIES)) {
                val category = showData.getInt(KEY_CATEGORIES)
                holder.categoryColorView?.visibility = View.VISIBLE

                if (category == 2 && showData.optInt(KEY_IS_MOVIE) == 0) { // Watching and it's a TV show
                    val movieId = showData.optInt(KEY_ID)
                    val totalEpisodes = getTotalEpisodesFromTmdb(context, movieId)
                    Log.d("ShowBaseAdapter", "Total episodes: $totalEpisodes")
                    val watchedEpisodes = getWatchedEpisodesCount(showData)
                    Log.d("ShowBaseAdapter", "Watched episodes: $watchedEpisodes")
                    val episodesLeft = totalEpisodes - watchedEpisodes

                    holder.categoryColorView?.text = context.getString(
                        R.string.ep_progress_text,
                        watchedEpisodes,
                        totalEpisodes,
                        episodesLeft
                    )

                    holder.watchedProgressView?.visibility = View.VISIBLE
                    holder.watchedProgressView?.progress = (watchedEpisodes.toFloat() / totalEpisodes.toFloat() * 100).toInt()
                } else {
                    val categoryText = when (category) {
                        0 -> "Plan to watch"
                        1 -> "Watched"
                        2 -> "Watching"
                        3 -> "On hold"
                        4 -> "Dropped"
                        else -> "Unknown"
                    }
                    holder.categoryColorView?.text = categoryText
                }
            } else {
                holder.categoryColorView?.visibility = View.GONE
                holder.watchedProgressView?.visibility = View.GONE
            }


            if (showData.has(IS_UPCOMING) && showData.getBoolean(IS_UPCOMING)) {
                val season = showData.optString("seasons")
                val episodeNumber = showData.optString("upcoming_episode_number")

                if (!season.isNullOrEmpty() && !episodeNumber.isNullOrEmpty()) {
                    holder.categoryColorView?.visibility = View.VISIBLE
                    holder.categoryColorView?.text = context.getString(
                        R.string.episode_s,
                        episodeNumber.toInt(), season.toInt()
                    )
                } else {
                    holder.categoryColorView?.visibility = View.GONE
                }
            }

            var dateString = when {
                showData.has(ListFragment.UPCOMING_DATE) -> showData.optString(ListFragment.UPCOMING_DATE)
                showData.has(KEY_DATE_MOVIE) -> showData.optString(KEY_DATE_MOVIE)
                else -> showData.optString(KEY_DATE_SERIES)
            }

            val formats = listOf(
                "yyyy-MM-dd",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "dd-MM-yyyy"
            )

            var parsedDate: Date? = null
            for (format in formats) {
                try {
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    parsedDate = formatter.parse(dateString)
                    if (parsedDate != null) break
                } catch (e: ParseException) {
                    continue
                }
            }

            if (parsedDate != null) {
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(parsedDate)
            }
            holder.showDate.text = dateString

            if (!mGridView) {
                holder.showDescription?.text = showData.optString(KEY_DESCRIPTION)
                holder.showRating?.rating = showData.optString(KEY_RATING).toFloat() / 2

                var genreIds = showData.optString(KEY_GENRES)
                genreIds = if (!genreIds.isNullOrEmpty() && genreIds.length > 2) {
                    genreIds.substring(1, genreIds.length - 1)
                } else {
                    ""
                }
                val genreArray = genreIds.split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
                val sharedPreferences = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
                val genreNames = StringBuilder()
                for (aGenreArray in genreArray) {
                    if (mGenreHashMap[aGenreArray] != null) {
                        genreNames.append(", ").append(mGenreHashMap[aGenreArray])
                    } else {
                        genreNames.append(", ").append(sharedPreferences.getString(aGenreArray, ""))
                    }
                }
                holder.showGenre?.text = if (genreNames.isNotEmpty()) genreNames.substring(2) else ""
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        if (showData.has(IS_MOVIE) && showData.optInt(IS_MOVIE) != 1) {
            holder.itemView.setOnLongClickListener {

                val bottomSheetDialog = BottomSheetDialog(context)
                val bottomSheetBinding = BottomSheetSeasonEpisodeBinding.inflate(LayoutInflater.from(context))
                val chipGroupSeasons = bottomSheetBinding.chipGroupSeasons
                val recyclerViewEpisodes = bottomSheetBinding.recyclerViewEpisodes

                recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

                val seasons = getSeasonsFromTmdbDatabase(showData.getInt(KEY_ID))
                val maxVisibleChips = 5
                var isExpanded = false

                // Create show more chip
                val showMoreChip = Chip(context).apply {
                    text = context.getString(R.string.show_more)
                    isCheckable = false
                    visibility = if (seasons.size > maxVisibleChips) View.VISIBLE else View.GONE
                }

                fun updateChipsVisibility() {
                    chipGroupSeasons.children.forEachIndexed { index, view ->
                        if (view != showMoreChip) {
                            view.visibility = if (isExpanded || index < maxVisibleChips) View.VISIBLE else View.GONE
                        }
                    }
                }

                // Add season chips
                seasons.forEach { seasonNumber ->
                    val chip = Chip(context).apply {
                        text = context.getString(R.string.season_p, seasonNumber)
                        isCheckable = true
                        setOnClickListener {
                            val episodes = getEpisodesForSeasonFromTmdbDatabase(showData.getInt(KEY_ID), seasonNumber)
                            val watchedEpisodes = getWatchedEpisodesFromDb(showData.getInt(KEY_ID), seasonNumber)
                            recyclerViewEpisodes.adapter = EpisodeSavedAdapter(
                                episodes,
                                watchedEpisodes,
                                showData.getInt(KEY_ID),
                                seasonNumber,
                                context
                            )
                        }
                    }
                    chipGroupSeasons.addView(chip)
                }

                // Add show more chip and set its click listener
                if (seasons.size > maxVisibleChips) {
                    chipGroupSeasons.addView(showMoreChip)
                    showMoreChip.setOnClickListener {
                        isExpanded = !isExpanded
                        showMoreChip.text = context.getString(
                            if (isExpanded) R.string.show_less else R.string.show_more
                        )
                        updateChipsVisibility()
                    }
                    updateChipsVisibility()
                }

                bottomSheetDialog.setContentView(bottomSheetBinding.root)
                bottomSheetDialog.show()
                true
            }
        }

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(IS_UPCOMING) && showData.getBoolean(IS_UPCOMING)) {
                val upcomingType = showData.optString("upcoming_type")
                intent.putExtra("isMovie", upcomingType == "movie")
            } else if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }
    }

    private fun getSeasonsFromTmdbDatabase(showId: Int): List<Int> {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )
        val seasons = if (cursor.moveToFirst()) {
            parseSeasonsTmdb(cursor.getString(cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)))
        } else {
            emptyList()
        }
        cursor.close()
        db.close()
        return seasons
    }

    private fun getEpisodesForSeasonFromTmdbDatabase(showId: Int, seasonNumber: Int): List<Int> {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )
        val episodes = if (cursor.moveToFirst()) {
            parseEpisodesForSeasonTmdb(cursor.getString(cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)), seasonNumber)
        } else {
            emptyList()
        }
        cursor.close()
        db.close()
        return episodes
    }

    private fun getWatchedEpisodesFromDb(showTraktId: Int, seasonNumber: Int): MutableList<Int> {
        val dbHelper = MovieDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MovieDatabaseHelper.TABLE_EPISODES,
            arrayOf(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER),
            "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ? AND ${MovieDatabaseHelper.COLUMN_SEASON_NUMBER} = ?",
            arrayOf(showTraktId.toString(), seasonNumber.toString()),
            null, null, null
        )
        val watchedEpisodes = mutableListOf<Int>()
        if (cursor.moveToFirst()) {
            do {
                val episodeNumber = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER))
                watchedEpisodes.add(episodeNumber)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return watchedEpisodes
    }

    private fun parseSeasonsTmdb(seasonsString: String): List<Int> {
        val regex = Regex("""(\d+)\{.*?\}""")
        return regex.findAll(seasonsString).map { it.groupValues[1].toInt() }.toList()
    }

    private fun parseEpisodesForSeasonTmdb(seasonsString: String, seasonNumber: Int): List<Int> {
        val regex = Regex("""$seasonNumber\{(\d+(,\d+)*)\}""")
        val matchResult = regex.find(seasonsString) ?: return emptyList()
        return matchResult.groupValues[1].split(",").map { it.toInt() }
    }

    fun updateData(newData: ArrayList<JSONObject>) {
        mShowArrayList.clear()
        mShowArrayList.addAll(newData)
        notifyDataSetChanged()
    }

    private fun getWatchedEpisodesCount(showData: JSONObject): Int {
        if (!showData.has("seasons")) return 0

        val seasons = showData.getJSONObject("seasons")
        var watchedCount = 0

        seasons.keys().forEach { seasonNumber ->
            val episodes = seasons.getJSONArray(seasonNumber)
            watchedCount += episodes.length()
        }

        return watchedCount
    }

    private fun getTotalEpisodesFromTmdb(context: Context, movieId: Int): Int {
        val tmdbDbHelper = TmdbDetailsDatabaseHelper(context)
        val tmdbDb = tmdbDbHelper.readableDatabase

        val cursor = tmdbDb.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(movieId.toString()),
            null, null, null
        )

        var totalEpisodes = 0
        if (cursor.moveToFirst()) {
            val seasonsEpisodeString = cursor.getString(
                cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)
            )

            if (!seasonsEpisodeString.isNullOrEmpty()) {
                val regex = Regex("""\d+\{(\d+(,\d+)*)\}""")
                totalEpisodes = regex.findAll(seasonsEpisodeString).sumOf { matchResult ->
                    matchResult.groupValues[1].split(",").size
                }
            }
        }

        cursor.close()
        tmdbDb.close()
        return totalEpisodes
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class ShowItemViewHolder(
        val binding: ShowCardBinding?,
        val gridBinding: ShowGridCardBinding?
    ) : RecyclerView.ViewHolder(gridBinding?.root ?: binding!!.root) {

        val showView = gridBinding?.cardView ?: binding!!.cardView
        val showTitle = gridBinding?.title ?: binding!!.title
        val showImage = gridBinding?.image ?: binding!!.image
        val showDescription = binding?.description
        val showGenre = binding?.genre
        val showRating = binding?.rating
        val showDate = gridBinding?.date ?: binding!!.date
        val deleteButton = gridBinding?.deleteButton ?: binding!!.deleteButton
        val categoryColorView = gridBinding?.categoryColor ?: binding?.categoryColor
        val watchedProgressView = gridBinding?.watchedProgress ?: binding?.watchedProgress
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_IMAGE = "backdrop_path"
        const val KEY_POSTER = "poster_path"
        const val KEY_TITLE = "title"
        const val KEY_NAME = "name"
        const val KEY_DESCRIPTION = "overview"
        const val KEY_RATING = "vote_average"
        const val KEY_DATE_MOVIE = "release_date"
        const val KEY_DATE_SERIES = "first_air_date"
        const val KEY_GENRES = "genre_ids"
        const val KEY_RELEASE_DATE = "release_date"
        const val KEY_IS_MOVIE = "is_movie"
        private const val HD_IMAGE_SIZE = "key_hq_images"
        private const val KEY_CATEGORIES = MovieDatabaseHelper.COLUMN_CATEGORIES
    }
}
