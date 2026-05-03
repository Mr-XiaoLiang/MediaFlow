package com.lollipop.mediaflow.page.archive

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lollipop.mediaflow.data.ArchiveBasket
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.databinding.ItemDialogArchiveSelectBinding
import com.lollipop.mediaflow.ui.dialog.BasicListSheetDialog

class ArchiveSelectDialog(
    context: Context,
    val onSelect: (ArchiveBasket) -> Unit
) : BasicListSheetDialog(context) {

    override fun onViewCreated(recyclerView: RecyclerView) {
        val dataList = mutableListOf<ArchiveBasket>()
        dataList.addAll(ArchiveManager.archiveBasketList)
        val itemAdapter = ItemAdapter(dataList, ::onItemSelect)
        recyclerView.adapter = ConcatAdapter(itemAdapter, BottomSpacerAdapter())
    }

    private fun onItemSelect(basket: ArchiveBasket) {
        onSelect(basket)
        dismiss()
    }

    private class ItemAdapter(
        val basketList: List<ArchiveBasket>,
        val onSelect: (ArchiveBasket) -> Unit
    ) : RecyclerView.Adapter<ItemHolder>() {

        private var layoutInflater: LayoutInflater? = null

        private fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ItemHolder {
            return ItemHolder(
                binding = ItemDialogArchiveSelectBinding.inflate(
                    getLayoutInflater(parent),
                    parent,
                    false
                ),
                onSelect = ::onSelect
            )
        }

        override fun onBindViewHolder(
            holder: ItemHolder,
            position: Int
        ) {
            val basket = basketList[position]
            holder.onBind(basket)
        }

        override fun getItemCount(): Int {
            return basketList.size
        }

        private fun onSelect(position: Int) {
            if (position < 0 || position >= basketList.size) {
                return
            }
            onSelect(basketList[position])
        }

    }

    private class ItemHolder(
        val binding: ItemDialogArchiveSelectBinding,
        val onSelect: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { onClick() }
        }

        private fun onClick() {
            onSelect(bindingAdapterPosition)
        }

        fun onBind(info: ArchiveBasket) {
            binding.archiveBasketNameView.text = info.name
            val quick = ArchiveManager.getBasketType(info)
            binding.archiveBasketIconView.setImageResource(quick.iconRes)
            binding.archiveBasketHintView.text = info.uriPath
        }

    }

}