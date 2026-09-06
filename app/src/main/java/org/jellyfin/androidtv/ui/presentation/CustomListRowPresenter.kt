package org.jellyfin.androidtv.ui.presentation

import android.view.View
import androidx.core.view.isVisible
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

open class CustomListRowPresenter @JvmOverloads constructor(
	private val topPadding: Int? = null
) : ListRowPresenter() {
	init {
		headerPresenter = CustomRowHeaderPresenter()
	}

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)

		// Wider gap between cards inside a row: card titles are always shown now
		// and would be cramped/cut off with the leanback default spacing
		if (holder is ListRowPresenter.ViewHolder) {
			holder.gridView.setItemSpacing((16 * holder.view.resources.displayMetrics.density).toInt())
		}

		val view = holder.view?.parent as? View ?: return
		if (topPadding != null) view.setPadding(view.paddingLeft, topPadding, view.paddingRight, view.paddingBottom)

		// Hide header view when the item doesn't have one
		holder.headerViewHolder.view.isVisible = !(item is ListRow && item.headerItem == null)
	}
}
