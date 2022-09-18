package eu.kanade.tachiyomi.ui.manga.stats

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ListStatsDetailsBinding
import eu.kanade.tachiyomi.ui.manga.stats.StatsMangaPresenter.StatsData
import eu.kanade.tachiyomi.ui.manga.stats.StatsMangaPresenter.StatsSort
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.util.system.getResourceColor

class StatsMangaAdapter(
    private val context: Context,
    var list: MutableList<StatsData>,
    private val selectedStatsSort: StatsSort,
) : RecyclerView.Adapter<StatsMangaAdapter.StatsMangaHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsMangaHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_stats_details, parent, false)
        return StatsMangaHolder(view)
    }

    override fun onBindViewHolder(holder: StatsMangaHolder, position: Int) {
        val item = list[position]
        holder.statsRankLayout.isVisible = selectedStatsSort == StatsSort.READ_DURATION_COUNT
        holder.statsMeanScoreLayout.isVisible = true
        holder.statsDataLayout.isVisible = false
        holder.statsScoreStarImage.isVisible = false

        holder.statsRankText.text = String.format("%02d.", position + 1)
        holder.statsLabelText.setTextColor(context.getResourceColor(R.attr.colorOnBackground))
        holder.statsLabelText.text = item.label
        holder.statsScoreText.text = item.readDuration.getReadDuration(context.getString(R.string.none))
        holder.statsSublabelText.isVisible = !item.subLabel.isNullOrBlank()
        holder.statsSublabelText.text = item.subLabel
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class StatsMangaHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val binding = ListStatsDetailsBinding.bind(view)

        val statsRankLayout = binding.statsRankLayout
        val statsRankText = binding.statsRankText
        val statsLabelText = binding.statsLabelText
        val statsMeanScoreLayout = binding.statsMeanScoreLayout
        val statsScoreText = binding.statsScoreText
        val statsDataLayout = binding.statsDataLayout
        val statsScoreStarImage = binding.statsScoreStarImage
        val statsSublabelText = binding.statsSublabelText
    }
}
