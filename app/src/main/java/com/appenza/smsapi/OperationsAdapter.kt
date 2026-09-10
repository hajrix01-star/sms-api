package com.appenza.smsapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Keeps the operations page bounded and recyclable even when the local ledger grows. */
class OperationsAdapter : RecyclerView.Adapter<OperationsAdapter.Holder>() {
    private val items = mutableListOf<LedgerEvent>()
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar"))
    private val amountFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun submit(events: List<LedgerEvent>) {
        items.clear()
        items.addAll(events)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_operation, parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val event = items[position]
        holder.category.text = event.category
        holder.direction.text = operationDirection(event)
        holder.amount.text = event.amount?.let { "SAR ${amountFormat.format(it)}" } ?: "—"
        holder.amount.setTextColor(ContextCompat.getColor(holder.itemView.context, amountColor(event)))
        val reference = event.instrument?.let { " · $it" }.orEmpty()
        val company = event.companyName?.let { "$it · " }.orEmpty()
        holder.meta.text = "$company${event.sender}$reference · ${dateFormat.format(Date(event.receivedAt))}"
        holder.counterparty.text = event.counterparty?.let { "الطرف: $it" }.orEmpty()
        holder.counterparty.visibility = if (event.counterparty.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.preview.text = event.body.replace(Regex("\\s+"), " ").trim()
    }

    private fun operationDirection(event: LedgerEvent): String = when {
        event.custodyType == "تمويل عهدة" -> "تغذية عهدة"
        event.custodyType == "مشتريات عهدة" -> "مصروف بطاقة العهدة"
        event.custodyType == "سحب نقدي عهدة" -> "نقد مع المندوب"
        event.category.contains("إيداع") || event.category.contains("تسوية") || event.category.contains("وارد") -> "داخل الحساب"
        event.category.contains("تحويل") -> "حركة تحويل"
        event.category.contains("سحب") -> "سحب نقدي"
        event.category.contains("شراء") || event.category.contains("POS") -> "عملية شراء"
        event.category.contains("رسوم") -> "مصروف بنكي"
        else -> event.companyName ?: "بانتظار الربط"
    }

    private fun amountColor(event: LedgerEvent): Int = when {
        event.category.contains("إيداع") || event.category.contains("تسوية") || event.category.contains("وارد") -> R.color.primary_action
        event.category.contains("رسوم") -> R.color.text_danger
        else -> R.color.text_primary
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val category: TextView = view.findViewById(R.id.operationCategory)
        val direction: TextView = view.findViewById(R.id.operationDirection)
        val amount: TextView = view.findViewById(R.id.operationAmount)
        val meta: TextView = view.findViewById(R.id.operationMeta)
        val counterparty: TextView = view.findViewById(R.id.operationCounterparty)
        val preview: TextView = view.findViewById(R.id.operationPreview)
    }
}
