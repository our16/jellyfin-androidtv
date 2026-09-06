package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.querying.GetSpecialsRequest
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

class GenericFolderFragment : EnhancedBrowseFragment() {
	companion object {
		private val showSpecialViewTypes = setOf(
			BaseItemKind.COLLECTION_FOLDER,
			BaseItemKind.FOLDER,
			BaseItemKind.USER_VIEW,
			BaseItemKind.CHANNEL_FOLDER_ITEM,
		)

		private const val PREFS_NAME = "browse_prefs"
		private const val PREF_SEASON_SORT_DESC = "season_sort_desc"
	}

	private lateinit var sortPrefs: SharedPreferences

	/**
	 * Menu key on a season page toggles episode order between
	 * ascending (1,2,3,4...) and descending (5,4,3,2...).
	 * Returns true when handled (i.e. this is a season page).
	 */
	fun toggleEpisodeSort(): Boolean {
		if (!::sortPrefs.isInitialized || mFolder.type != BaseItemKind.SEASON) return false
		val desc = !sortPrefs.getBoolean(PREF_SEASON_SORT_DESC, false)
		sortPrefs.edit().putBoolean(PREF_SEASON_SORT_DESC, desc).apply()
		Toast.makeText(
			requireContext(),
			if (desc) R.string.msg_episode_sort_desc else R.string.msg_episode_sort_asc,
			Toast.LENGTH_SHORT,
		).show()
		// Rebuild the page so the row reloads with the new sort order
		navigationRepository.value.navigate(Destinations.folderBrowser(mFolder), true)
		return true
	}

	override fun setupQueries(rowLoader: RowLoader) {
		sortPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

		if ((mFolder.childCount == null || mFolder.childCount == 0) && !setOf(
				BaseItemKind.CHANNEL,
				BaseItemKind.CHANNEL_FOLDER_ITEM,
				BaseItemKind.USER_VIEW,
				BaseItemKind.COLLECTION_FOLDER,
			).contains(mFolder.type)
		) return

		if (showSpecialViewTypes.contains(mFolder.type)) {
			if (mFolder.type != BaseItemKind.CHANNEL_FOLDER_ITEM) {
				val resume = GetItemsRequest(
					fields = ItemRepository.itemFields,
					parentId = mFolder.id,
					limit = 50,
					filters = setOf(ItemFilter.IS_RESUMABLE),
					sortBy = setOf(ItemSortBy.DATE_PLAYED),
					sortOrder = setOf(SortOrder.DESCENDING),
				)
				mRows.add(BrowseRowDef(getString(R.string.lbl_continue_watching), resume, 0))
			}

			val latest = GetItemsRequest(
				fields = ItemRepository.itemFields,
				parentId = mFolder.id,
				limit = 50,
				filters = setOf(ItemFilter.IS_UNPLAYED),
				sortBy = setOf(ItemSortBy.DATE_CREATED),
				sortOrder = setOf(SortOrder.DESCENDING),
			)
			mRows.add(BrowseRowDef(getString(R.string.lbl_latest), latest, 0))
		}

		// Episodes of a season are ordered by episode index (1,2,3...) instead of sort name,
		// which would interleave 1,10,11,2... The order direction is switchable via menu key.
		val byName = when (mFolder.type) {
			BaseItemKind.SEASON -> {
				val desc = sortPrefs.getBoolean(PREF_SEASON_SORT_DESC, false)
				GetItemsRequest(
					fields = ItemRepository.itemFields,
					parentId = mFolder.id,
					sortBy = setOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
					sortOrder = setOf(if (desc) SortOrder.DESCENDING else SortOrder.ASCENDING),
				)
			}
			else -> GetItemsRequest(
				fields = ItemRepository.itemFields,
				parentId = mFolder.id,
			)
		}
		val header = when (mFolder.type) {
			BaseItemKind.SEASON -> mFolder.name
			else -> getString(R.string.lbl_by_name)
		}

		mRows.add(BrowseRowDef(header, byName, 100))

		if (mFolder.type == BaseItemKind.SEASON) {
			val specials = GetSpecialsRequest(mFolder.id)
			mRows.add(BrowseRowDef(getString(R.string.lbl_specials), specials))
		}

		rowLoader.loadRows(mRows)
	}
}
