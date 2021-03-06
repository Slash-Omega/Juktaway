package net.slash_omega.juktaway

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ListView
import de.greenrobot.event.EventBus
import jp.nephy.jsonkt.stringify
import jp.nephy.penicillin.endpoints.lists
import jp.nephy.penicillin.endpoints.lists.list
import jp.nephy.penicillin.extensions.await
import jp.nephy.penicillin.models.TwitterList
import kotlinx.android.synthetic.main.activity_choose_user_lists.*
import kotlinx.coroutines.launch
import net.slash_omega.juktaway.adapter.SubscribeUserListAdapter
import net.slash_omega.juktaway.event.AlertDialogEvent
import net.slash_omega.juktaway.event.model.DestroyUserListEvent
import net.slash_omega.juktaway.model.UserListCache
import net.slash_omega.juktaway.model.UserListWithRegistered
import net.slash_omega.juktaway.twitter.currentClient
import net.slash_omega.juktaway.util.ThemeUtil

/**
 * Created on 2018/08/29.
 */
class ChooseUserListsActivity: ScopedFragmentActivity() {
    private lateinit var mAdapter: SubscribeUserListAdapter
    private lateinit var initial: List<UserListWithRegistered>

    @SuppressLint("UseSparseArrays")
    override fun onCreate(savedInstanceState: Bundle?) {
        launch {
            //TODO
            val lists = currentClient.lists.list().await()
            initial = lists.map { UserListWithRegistered(it) }
            mAdapter.addAll(initial.map { it.copy() })
            UserListCache.userLists = lists.toMutableList()
        }

        super.onCreate(savedInstanceState)
        ThemeUtil.setTheme(this)
        setContentView(R.layout.activity_choose_user_lists)

        actionBar?.run {
            setDisplayHomeAsUpEnabled(true)
        }

        val listView = findViewById<ListView>(R.id.list)
        mAdapter = SubscribeUserListAdapter(this, R.layout.row_subscribe_user_list)
        listView.adapter = mAdapter

        button_cancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        button_save.setOnClickListener { _ ->
            val addList = mutableListOf<TwitterList>()
            val removeList = mutableListOf<TwitterList>()
            for (i in 0 until mAdapter.count) {
                val lr = mAdapter.getItem(i)!!
                if (lr.isRegistered != initial[i].isRegistered) {
                    if (lr.isRegistered) addList.add(lr.userList)
                    else removeList.add(lr.userList)
                }
            }
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("add", addList.map{ it.stringify() }.toTypedArray())
                putExtra("remove", removeList.map{ it.stringify() }.toTypedArray())
            })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    fun onEventMainThread(event: AlertDialogEvent) { event.dialogFragment.show(supportFragmentManager, "dialog") }

    fun onEventMainThread(event: DestroyUserListEvent) {
        mAdapter.findByUserListId(event.userListId)?.let {
            mAdapter.remove(it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return true
    }
}
