package com.calebjcox.countdownwidgets.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.databinding.ItemTimerBinding

class TimerListAdapter(
    private val onClick: (Timer) -> Unit,
) : RecyclerView.Adapter<TimerListAdapter.Holder>() {

    private var items: List<Timer> = emptyList()

    // The list is a handful of rows reloaded on resume; a diff would cost more code
    // than it saves.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(timers: List<Timer>) {
        items = timers
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTimerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemTimerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(timer: Timer) {
            val context = binding.root.context
            binding.name.text = timer.name
            binding.value.text = TimerSummary.value(timer)
            binding.footer.text = TimerSummary.target(context, timer.spec)
            binding.root.setOnClickListener { onClick(timer) }
        }
    }
}
