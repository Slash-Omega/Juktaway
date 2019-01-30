package net.slash_omega.juktaway.fragment.main.tab

import android.view.View
import jp.nephy.penicillin.endpoints.favorites
import jp.nephy.penicillin.endpoints.favorites.list
import jp.nephy.penicillin.extensions.await
import net.slash_omega.juktaway.model.FavRetweetManager
import net.slash_omega.juktaway.model.TabManager
import net.slash_omega.juktaway.settings.BasicSettings
import net.slash_omega.juktaway.twitter.currentClient

class FavoritesFragment: BaseFragment() {
    override var tabId = TabManager.FAVORITES_TAB_ID

    override suspend fun taskExecute() {
        val statuses = runCatching {
            currentClient.favorites.list(
                    maxId = mMaxId.takeIf { it > 0 && !mReloading }?.minus(1),
                    count = BasicSettings.pageCount
            ).await()
        }.getOrNull()

        when {
            statuses.isNullOrEmpty() -> {
                mReloading = false
                mPullToRefreshLayout.setRefreshComplete()
                mListView.visibility = View.VISIBLE
            }
            mReloading -> {
                clear()
                for (status in statuses) {
                    FavRetweetManager.setFav(status.id)
                }
                mMaxId = statuses.last().id
                mAdapter?.extensionAddAllFromStatuses(statuses)
                mReloading = false
                mPullToRefreshLayout.setRefreshComplete()
            }
            else -> {
                for (status in statuses) {
                    FavRetweetManager.setFav(status.id)
                }
                mMaxId = statuses.last().id
                mAdapter?.extensionAddAllFromStatuses(statuses)
                mAutoLoader = true
                mListView.visibility = View.VISIBLE
            }
        }

        finishLoad()
    }
}