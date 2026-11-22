package com.example.assemblygame.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.assemblygame.R
import com.example.assemblygame.game.Level

/** Sectioned level list: header rows (CAMPAIGN / CUSTOM LEVELS ...) + playable rows. */
class LevelAdapter(
    private val onClick: (Level) -> Unit,
    private val onLongClick: (Level) -> Boolean = { false }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Header(val title: String) : Row()
        data class Game(val level: Level, val unlocked: Boolean, val stars: Int) : Row()
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_GAME = 1
    }

    private var rows: List<Row> = emptyList()

    fun submit(rows: List<Row>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.sectionTitle)
    }

    class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val numberText: TextView = view.findViewById(R.id.levelNumber)
        val titleText: TextView = view.findViewById(R.id.levelTitle)
        val taskText: TextView = view.findViewById(R.id.levelTask)
        val starsText: TextView = view.findViewById(R.id.levelStars)
        val lockText: TextView = view.findViewById(R.id.levelLock)
    }

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_GAME

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderViewHolder(inf.inflate(R.layout.item_header, parent, false))
        else
            GameViewHolder(inf.inflate(R.layout.item_level, parent, false))
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).title.text = row.title
            is Row.Game -> {
                val h = holder as GameViewHolder
                h.numberText.text = if (row.level.id >= 1000) "✎" else row.level.id.toString()
                h.titleText.text = row.level.title
                h.taskText.text = row.level.task.lineSequence().firstOrNull() ?: ""
                h.starsText.text = buildString {
                    repeat(3) { i -> append(if (i < row.stars) "★" else "☆") }
                }
                h.starsText.visibility = if (row.unlocked) View.VISIBLE else View.GONE
                h.lockText.visibility = if (row.unlocked) View.GONE else View.VISIBLE
                h.itemView.alpha = when {
                    !row.unlocked -> 0.45f
                    row.stars > 0 -> 0.85f
                    else -> 1f
                }
                h.itemView.setOnClickListener { onClick(row.level) }
                h.itemView.setOnLongClickListener { onLongClick(row.level) }
            }
        }
    }
}
