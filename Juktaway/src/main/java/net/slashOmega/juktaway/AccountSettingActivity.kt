package net.slashOmega.juktaway

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.view.Menu
import android.view.MenuItem
import net.slashOmega.juktaway.adapter.account.IdentifierAdapter
import net.slashOmega.juktaway.fragment.dialog.AccountSwitchDialogFragment
import net.slashOmega.juktaway.listener.OnTrashListener
import net.slashOmega.juktaway.listener.RemoveAccountListener
import net.slashOmega.juktaway.util.ThemeUtil
import kotlinx.android.synthetic.main.activity_account_setting.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.slashOmega.juktaway.twitter.Core
import net.slashOmega.juktaway.twitter.Identifier
import net.slashOmega.juktaway.twitter.currentIdentifier
import net.slashOmega.juktaway.twitter.identifierList
import org.jetbrains.anko.startActivity

/**
 * Created on 2018/08/23.
 */
class AccountSettingActivity: FragmentActivity(), RemoveAccountListener {
    private lateinit var mAccountAdapter: IdentifierAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.setTheme(this)
        setContentView(R.layout.activity_account_setting)

        actionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        mAccountAdapter = IdentifierAdapter(this, R.layout.row_account) .apply {
            identifierList.forEach { add(it) }
            mOnTrashListener = object: OnTrashListener {
                override fun onTrash(position: Int) { getItem(position)?.let {
                    AccountSwitchDialogFragment.newInstance(it).show(supportFragmentManager, "dialog")
                }}
            }
        }

        with (list_view) {
            adapter = mAccountAdapter
            setOnItemClickListener { _, _, i, _ ->
                mAccountAdapter.getItem(i).also {
                    if (it?.userId != currentIdentifier.userId) {
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra("identifier", it)
                        })
                        finish()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.account_setting, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.add_account -> startActivity<SignInActivity>("add_account" to true)
            android.R.id.home -> finish()
        }
        return true
    }

    override fun removeIdentifier(identifier: Identifier) {
        mAccountAdapter.remove(identifier)
        GlobalScope.launch(Dispatchers.Main) { Core.removeIdentifier(identifier) }
    }
}