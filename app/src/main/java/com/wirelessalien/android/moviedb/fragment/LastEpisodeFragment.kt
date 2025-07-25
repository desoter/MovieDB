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

import android.content.Context
import android.graphics.Color
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.FragmentLastEpisodeBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Locale

class LastEpisodeFragment : Fragment() {
    private lateinit var binding: FragmentLastEpisodeBinding
    private lateinit var databaseHelper: MovieDatabaseHelper
    private var movieId = 0
    private var seasonNumber = 0
    private var episodeNumber = 0
    private var episodeName: String? = null

    interface OnUpcomingEpisodeClickListener {
        fun onUpcomingEpisodeClicked(showId: Int, seasonNumber: Int, episodeNumber: Int, episodeName: String?)
    }
    private var upcomingEpisodeClickListener: OnUpcomingEpisodeClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnUpcomingEpisodeClickListener) {
            upcomingEpisodeClickListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        upcomingEpisodeClickListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLastEpisodeBinding.inflate(inflater, container, false)
        val view: View = binding.root
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getBoolean(DYNAMIC_COLOR_DETAILS_ACTIVITY, false)) {
            binding.lastEpisodeCard.strokeWidth = 5
            binding.lastEpisodeCard.setCardBackgroundColor(Color.TRANSPARENT)
        }
//        val sessionId = preferences.getString("access_token", null)
//        val accountId = preferences.getString("account_id", null)
        databaseHelper = MovieDatabaseHelper(context)
//        binding.episodeRateBtn.isEnabled = sessionId != null && accountId != null
        if (arguments != null) {
            try {
                val episodeDetails = JSONObject(requireArguments().getString(ARG_EPISODE_DETAILS).toString())
                movieId = episodeDetails.getInt("show_id")
                seasonNumber = episodeDetails.getInt("season_number")
                episodeNumber = episodeDetails.getInt("episode_number")
                episodeName = episodeDetails.getString("name")
                val labelText = requireArguments().getString(ARG_LABEL_TEXT)
                binding.episodeRecencyText.text = labelText
                binding.seasonNo.text = getString(R.string.season_number, seasonNumber)
                binding.episodeNo.text = getString(R.string.episode_number, episodeNumber)
                binding.episodeName.text = episodeName
//                binding.ratingAverage.text = String.format(
//                    Locale.getDefault(),
//                    "%.2f/%s",
//                    episodeDetails.getDouble("vote_average"),
//                    String.format(Locale.getDefault(), "%d", 10))

                val overview = episodeDetails.getString("overview")
                if (overview.isEmpty()) {
                    binding.episodeOverview.setText(R.string.overview_not_available)
                } else {
                    binding.episodeOverview.text = overview
                }
                val episodeAirDateStr = episodeDetails.getString("air_date")
                val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = originalFormat.parse(episodeAirDateStr)
                val dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                val formattedDate = dateFormat.format(date)
                binding.episodeAirDate.text = formattedDate
//                if (databaseHelper.isEpisodeInDatabase(movieId, seasonNumber, listOf(episodeNumber))) {
//                    binding.episodeWathchBtn.icon =
//                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility)
//                } else {
//                    binding.episodeWathchBtn.icon =
//                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility_off)
//                }
//                binding.episodeRateBtn.setOnClickListener {
//                    val dialog = BottomSheetDialog(requireContext())
//                    val dialogView = RatingDialogBinding.inflate(LayoutInflater.from(context))
//                    dialog.setContentView(dialogView.root)
//                    dialog.show()
//                    val ratingBar = dialogView.ratingSlider
//                    val submitButton = dialogView.btnSubmit
//                    val cancelButton = dialogView.btnCancel
//                    val deleteButton = dialogView.btnDelete
//                    val episodeTitle = dialogView.tvTitle
//                    episodeTitle.text = getString(R.string.episode_title, seasonNumber, episodeNumber, episodeName)
//                    submitButton.setOnClickListener {
//                        CoroutineScope(Dispatchers.Main).launch {
//                            val rating = ratingBar.value.toDouble()
//                            AddEpisodeRating(
//                                movieId,
//                                seasonNumber,
//                                episodeNumber,
//                                rating,
//                                context
//                            ).addRating()
//                            dialog.dismiss()
//                        }
//                    }
//
//                    deleteButton.setOnClickListener {
//                        CoroutineScope(Dispatchers.Main).launch {
//                            DeleteEpisodeRating(
//                                movieId,
//                                seasonNumber,
//                                episodeNumber,
//                                context
//                            ).deleteEpisodeRating()
//                            dialog.dismiss()
//                        }
//                    }
//                    cancelButton.setOnClickListener { dialog.dismiss() }
//                }
//
//                binding.episodeWathchBtn.setOnClickListener {
//                    if (databaseHelper.isEpisodeInDatabase(movieId, seasonNumber, listOf(episodeNumber))) {
//                        Log.d("Episode", "Removed from database $movieId $seasonNumber $episodeNumber")
//                        databaseHelper.removeEpisodeNumber(movieId, seasonNumber, listOf(episodeNumber))
//                        binding.episodeWathchBtn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility_off)
//                    } else {
//                        databaseHelper.addEpisodeNumber(movieId, seasonNumber, listOf(episodeNumber))
//                        Log.d("Episode", "Added to database $movieId $seasonNumber $episodeNumber")
//                        binding.episodeWathchBtn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility)
//                    }
//                }
            } catch (e: JSONException) {
                e.printStackTrace()
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }

        binding.lastEpisodeCard.setOnClickListener {
            if (upcomingEpisodeClickListener != null) {
                val episodeDetailsJson = JSONObject(requireArguments().getString(ARG_EPISODE_DETAILS).toString())
                val currentShowId = episodeDetailsJson.optInt("show_id", -1)
                val currentSeasonNumber = episodeDetailsJson.optInt("season_number", -1)
                val currentEpisodeNumber = episodeDetailsJson.optInt("episode_number", -1)
                val currentEpisodeName = episodeDetailsJson.optString("name")

                if (currentShowId != -1 && currentSeasonNumber != -1 && currentEpisodeNumber != -1) {
                    upcomingEpisodeClickListener?.onUpcomingEpisodeClicked(currentShowId, currentSeasonNumber, currentEpisodeNumber, currentEpisodeName)
                } else {
                    Log.e("LastEpisodeFragment", "Episode details missing for click.")
                }
            }
        }
        return view
    }

    companion object {
        private const val ARG_EPISODE_DETAILS = "episode_details"
        private const val ARG_LABEL_TEXT = "label_text"
        private const val DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity"
        fun newInstance(episodeDetails: JSONObject, labelText: String?): LastEpisodeFragment {
            val fragment = LastEpisodeFragment()
            val args = Bundle()
            args.putString(ARG_EPISODE_DETAILS, episodeDetails.toString())
            args.putString(ARG_LABEL_TEXT, labelText)
            fragment.arguments = args
            return fragment
        }
    }
}