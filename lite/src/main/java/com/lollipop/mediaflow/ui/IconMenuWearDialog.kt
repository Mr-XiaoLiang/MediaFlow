package com.lollipop.mediaflow.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lollipop.common.databinding.ItemMenuPopBinding
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.ui.page.fetchCallback
import com.lollipop.common.ui.view.IconPopupMenu.MenuItemEntity

abstract class IconMenuWearDialog : BasicListWearDialog() {

    private var callback: OnMenuItemClickListener? = null

    private val log by lazy {
        registerLog()
    }

    abstract fun getMenuList(context: Context): List<MenuItemEntity>

    private fun onMenuItemClick(item: MenuItemEntity) {
        callback?.onMenuItemClick(item)
        dismiss()
    }

    override fun onViewCreated(recyclerView: RecyclerView) {
        val dataList = getMenuList(recyclerView.context)
        log.i("dataList: $dataList")
        val adapterGroup = createAdapter(MenuAdapter(dataList, ::onMenuItemClick))
        recyclerView.adapter = adapterGroup.concat
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = fetchCallback<OnMenuItemClickListener>(context)
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    private class MenuAdapter(
        private val dataList: List<MenuItemEntity>,
        private val onClick: (MenuItemEntity) -> Unit
    ) : RecyclerView.Adapter<ItemHolder>() {

        private var layoutInflater: LayoutInflater? = null

        private fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun getItemCount(): Int {
            return dataList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            return ItemHolder(
                ItemMenuPopBinding.inflate(getLayoutInflater(parent), parent, false),
                onClick
            )
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            holder.bindView(dataList[position])
        }
    }

    private class ItemHolder(
        val binding: ItemMenuPopBinding,
        val onClickCallback: (MenuItemEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindView(data: MenuItemEntity) {
            binding.titleView.setText(data.titleRes)
            binding.iconView.setImageResource(data.iconRes)
            binding.iconView.isVisible = data.iconRes != 0
            binding.root.setOnClickListener(ClickWrapper(data, onClickCallback))
        }

    }

    private class ClickWrapper(
        private val info: MenuItemEntity,
        private val onClick: (MenuItemEntity) -> Unit
    ) : View.OnClickListener {
        override fun onClick(v: View?) {
            onClick(info)
        }
    }

    interface OnMenuItemClickListener {
        fun onMenuItemClick(item: MenuItemEntity)
    }

}