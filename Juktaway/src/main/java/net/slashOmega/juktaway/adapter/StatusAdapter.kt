package net.slashOmega.juktaway.adapter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.Log
import android.util.TimingLogger
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import de.greenrobot.event.EventBus
import jp.nephy.jsonkt.parse
import jp.nephy.jsonkt.toJsonObject
import jp.nephy.jsonkt.toJsonString
import jp.nephy.penicillin.extensions.complete
import jp.nephy.penicillin.extensions.createdAt
import jp.nephy.penicillin.extensions.via
import jp.nephy.penicillin.models.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.slashOmega.juktaway.ProfileActivity
import net.slashOmega.juktaway.R
import net.slashOmega.juktaway.StatusActivity
import net.slashOmega.juktaway.event.AlertDialogEvent
import net.slashOmega.juktaway.layouts.fontelloTextView
import net.slashOmega.juktaway.model.FavRetweetManager
import net.slashOmega.juktaway.model.Row
import net.slashOmega.juktaway.model.displayUserIcon
import net.slashOmega.juktaway.settings.BasicSettings
import net.slashOmega.juktaway.settings.mute.Mute
import net.slashOmega.juktaway.twitter.currentClient
import net.slashOmega.juktaway.twitter.currentIdentifier
import net.slashOmega.juktaway.util.*
import net.slashOmega.juktaway.util.TwitterUtil.omitCount
import org.jetbrains.anko.*
import java.util.*

/**
 * Created on 2018/11/13.
 */

class StatusAdapter(private val mContext: Context) : ArrayAdapter<Row>(mContext, 0) {
    companion object {
        class DestroyRetweetDialogFragment: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?)
                    = arguments?.getString("status")?.toJsonObject()?.parse(Status::class)?.let {
                AlertDialog.Builder(activity)
                        .setTitle(R.string.confirm_destroy_retweet)
                        .setMessage(it.text)
                        .setPositiveButton(getString(R.string.button_destroy_retweet)) { _, _  ->
                            GlobalScope.launch(Dispatchers.Main) {
                                ActionUtil.doDestroyRetweet(it)
                                dismiss()
                            }
                        }
                        .setNegativeButton(getString(R.string.button_cancel)) { _, _ -> dismiss() }
                        .create()
            } ?: throw IllegalStateException()
        }

        class RetweetDialogFragment: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?)
                    = arguments?.getString("status")?.toJsonObject()?.parse(Status::class)?.let {
                AlertDialog.Builder(activity)
                        .setTitle(R.string.confirm_retweet)
                        .setMessage(it.text)
                        .setNeutralButton(R.string.button_quote) { _, _ ->
                            ActionUtil.doQuote(it, activity!!)
                            dismiss()
                        }
                        .setPositiveButton(R.string.button_retweet) { _, _ ->
                            GlobalScope.launch(Dispatchers.Main) {
                                ActionUtil.doRetweet(it.id)
                                dismiss()
                            }
                        }
                        .setNegativeButton(R.string.button_cancel) { _, _ -> dismiss() }
                        .create()
            } ?: throw IllegalStateException()
        }
    }

    private val limit = 100
    private var mLimit = limit
    private val mIdSet = Collections.synchronizedSet(mutableSetOf<Long>())

    fun extensionAdd(row: Row) {
        GlobalScope.launch(Dispatchers.Main) {
            if (withContext (Dispatchers.Default) { row in Mute || exists(row) }) return@launch
            super.add(row)
            if (row.isStatus) mIdSet.add(row.status!!.id)
            filter(row)
            mLimit++
        }
    }

    fun extensionAddAll(rows: List<Row>) {
        GlobalScope.launch(Dispatchers.Main) {
            val statuses = withContext(Dispatchers.Default) {
                rows.filter { it !in Mute && !exists(it) }
            }
            launch(Dispatchers.Default) {
                mIdSet.addAll(statuses.filter { it.isStatus }.map { it.status!!.id })
            }
            statuses.forEach {
                super.add(it)
                filter(it)
            }
            mLimit += rows.size
        }
    }

    fun extensionAddAllFromStatuses(statusesParam: List<Status>) {
        GlobalScope.launch(Dispatchers.Main) {
            extensionAddAllFromStatusesSuspend(statusesParam)
        }
    }

    suspend fun extensionAddAllFromStatusesSuspend(statusesParam: List<Status>) {
        val logger = TimingLogger("TIMING_LOGGER", "addall")
        val statuses = withContext(Dispatchers.Default) {
            Mute.filterAll(statusesParam).map { Row.newStatus(it) }.filter { !exists(it) }
        }
        logger.addSplit("filtering")
        Dispatchers.Default.doAsync {
            mIdSet.addAll(statuses.map { it.status!!.id })
        }
        logger.addSplit("launching")

        filterAll(statuses)
        super.addAll(statuses)
        mLimit += statuses.size
        logger.addSplit("finished")
        logger.dumpToLog()
    }

    override fun add(row: Row) {
        GlobalScope.launch(Dispatchers.Main) { addSuspend(row) }
    }

    suspend fun addSuspend(row:Row) {
        if (withContext(Dispatchers.Default) { row in Mute || exists(row) }) return
        super.add(row)
        if (row.isStatus) mIdSet.add(row.status!!.id)
        filter(row)
        limitation()
    }

    suspend fun addAll(rows: List<Row>) {
        val statuses = withContext(Dispatchers.Default) {
            rows.filter { it !in Mute && !exists(it) }
        }
        Dispatchers.Default.doAsync {
            mIdSet.addAll(statuses.filter { it.isStatus }.map { it.status!!.id })
        }

        filterAll(statuses)
        super.addAll(statuses)

        limitation()
    }

    fun addAllFromStatuses(statusesParam: List<Status>) {
        GlobalScope.launch(Dispatchers.Main) {
            addAllFromStatusesSuspend(statusesParam)
        }
    }

    suspend fun addAllFromStatusesSuspend(statusesParam: List<Status>) {
        val statuses = withContext(Dispatchers.Default) {
            Mute.filterAll(statusesParam).map { Row.newStatus(it) }.filter { !exists(it) }
        }
        Dispatchers.Default.doAsync { mIdSet.addAll(statuses.map { it.status!!.id }) }

        filterAll(statuses)
        super.addAll(statuses)

        // limitation()
    }

    override fun insert(row: Row, index: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            insertSuspend(row, index)
        }
    }

    suspend fun insertSuspend(row: Row, index: Int) {
        if (withContext(Dispatchers.Default) { row in Mute || exists(row) }) return
        super.insert(row, index)
        if (row.isStatus) mIdSet.add(row.status!!.id)
        filter(row)
        // limitation()
    }

    override fun remove(row: Row) {
        super.remove(row)
        if (row.isStatus) mIdSet.add(row.status!!.id)
    }

    private fun exists(row: Row) = row.isStatus && row.status!!.id in mIdSet

    private suspend fun filter(row: Row) {
        withContext(Dispatchers.Default) {
            row.status?.takeIf { it.retweeted }?.let { status ->
                status.retweetedStatus?.takeIf { status.user.id == currentIdentifier.userId }?.let { retweet ->
                    FavRetweetManager.setRtId(retweet.id, status.id)
                }
            }
        }
    }

    private fun filterAll(rows: Collection<Row>) {
        Dispatchers.Default.doAsync {
            rows.filter { it.status?.retweetedStatus?.user?.id == currentIdentifier.userId }
                    .map { it.status!! to it.status!!.retweetedStatus!! }
                    .forEach { (status, retweet) ->
                        FavRetweetManager.setRtId(retweet.id, status.id)
                    }
        }
    }

    @Suppress("Unused")
    fun replaceStatus(status: Status) {
        for (i in 0 until count) {
            getItem(i)?.takeIf { it.isDirectMessage && it.status?.id == status.id }?.let {
                it.status = status
                notifyDataSetChanged()
                return
            }
        }
    }

    suspend fun removeStatus(id: Long): List<Int> {
        var pos = 0
        val positions = mutableListOf<Int>()
        val rows = mutableListOf<Row>()

        withContext(Dispatchers.Default) {
            for (i in 0 until count) {
                val row = getItem(i)?.takeUnless { it.isDirectMessage } ?: continue
                val status = row.status
                val retweet = status!!.retweetedStatus
                if (status.id == id || retweet?.id == id) {
                    rows.add(row)
                    positions.add(pos++)
                }
            }
        }
        for (row in rows) remove(row)
        return positions
    }

    fun removeDirectMessage(directMessageId: Long) {
        for (i in 0 until count) {
            val row = getItem(i)?.takeUnless { it.isDirectMessage } ?: continue
            if (row.message!!.id == directMessageId) {
                remove(row)
                break
            }
        }
    }

    private fun limitation() {
        if (count > mLimit) {
            val count = count - mLimit
            for (i in 0 until count) {
                super.remove(getItem(count - i - 1))
            }
        }
    }

    override fun clear() {
        super.clear()
        mIdSet.clear()
        mLimit = limit
    }

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = parent.context.run {
        getItem(position)!!.takeIf { it.isStatus }?.let { row ->
            row.status?.let { status ->
                val s = status.retweetedStatus ?: status
                val fontSize = BasicSettings.fontSize.toFloat()
                relativeLayout {
                    bottomPadding = dip(3)
                    leftPadding = dip(6)
                    rightPadding = dip(7)
                    topPadding = dip(4)


                    val data = s.retweetedStatus?.run {
                        RowData(R.string.fontello_retweet, ContextCompat.getColor(mContext, R.color.holo_green_light),
                                user.name, user.screenName)

                    } ?: RowData(R.string.fontello_at, ContextCompat.getColor(mContext, R.color.holo_red_light),
                            s.user.name, s.user.screenName)

                    val actionContainer = relativeLayout {
                        id = R.id.action_container

                        fontelloTextView {
                            id = R.id.action_icon
                            gravity = Gravity.END
                            textSize = 12f //sp
                            setText(data.textId)
                            textColor = data.textColor
                        }.lparams(width = dip(48), height = wrapContent) {
                            rightMargin = dip(6)
                            bottomMargin = dip(2)
                        }

                        textView {
                            id = R.id.action_by_display_name
                            textSize = 12f //sp
                            setTypeface(typeface, Typeface.BOLD)
                            text = data.displayName
                        }.lparams(width = wrapContent, height = wrapContent) {
                            rightOf(R.id.action_icon)
                        }

                        textView {
                            id = R.id.action_by_screen_name
                            textColor = Color.parseColor("#666666")
                            textSize = 10f //sp
                            text = data.screenName
                        }.lparams(width = wrapContent, height = wrapContent) {
                            rightOf(R.id.action_by_display_name)
                            leftMargin = dip(4)
                        }
                    }.lparams(width = matchParent)

                    imageView {
                        id = R.id.icon
                        topPadding = dip(2)
                        contentDescription = resources.getString(R.string.description_icon)
                        setOnClickListener {
                            startActivity(it.context.intentFor<ProfileActivity>("screenName" to s.user.screenName))
                        }

                        GlobalScope.launch(Dispatchers.Main) { displayUserIcon(s.user) }
                    }.lparams(width = dip(48), height = dip(48)) {
                        below(R.id.action_container)
                        bottomMargin = dip(6)
                        rightMargin = dip(6)
                        topMargin = dip(1)
                    }

                    textView {
                        id = R.id.display_name
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                        setTypeface(typeface, Typeface.BOLD)
                        text = s.user.name
                    }.lparams {
                        below(R.id.action_container)
                        rightOf(R.id.icon)
                        bottomMargin = dip(6)
                    }

                    textView {
                        id = R.id.screen_name
                        textColor = Color.parseColor("#666666")
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize - 2)
                        text = "@" + s.user.screenName
                        lines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }.lparams {
                        leftMargin = dip(4)
                        rightOf(R.id.display_name)
                        baselineOf(R.id.display_name)
                    }
                    if (s.user.protected) {
                        fontelloTextView {
                            id = R.id.lock
                            text = resources.getString(R.string.fontello_lock)
                            textColor = Color.parseColor("#666666")
                            textSize = 10f //sp
                            //tools:ignore = SmallSp //not support attribute
                        }.lparams {
                            rightOf(R.id.screen_name)
                            baselineOf(R.id.display_name)
                            leftMargin = dip(4)
                        }
                    }

                    textView {
                        id = R.id.datetime_relative
                        textColor = Color.parseColor("#666666")
                        setTextSize(TypedValue.COMPLEX_UNIT_SP,fontSize - 2)
                        text = TimeUtil.getRelativeTime(s.createdAt.date)
                    }.lparams {
                        alignParentRight()
                        baselineOf(R.id.display_name)
                    }

                    textView {
                        id = R.id.status
                        tag = fontSize
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                        text = StatusUtil.generateUnderline(StatusUtil.getExpandedText(s))
                    }.lparams {
                        rightOf(R.id.icon)
                        below(R.id.display_name)
                    }

                    relativeLayout {
                        id = R.id.quoted_tweet
                        s.quotedStatus?.let { qs ->
                            padding = dip(10)
                            backgroundResource = R.drawable.quoted_tweet_frame

                            textView {
                                id = R.id.quoted_display_name
                                textSize = 12f //sp
                                setTypeface(typeface, Typeface.BOLD)
                                text = qs.user.name
                            }.lparams {
                                bottomMargin = dip(6)
                            }

                            textView {
                                id = R.id.quoted_screen_name
                                textColor = Color.parseColor("#666666")
                                textSize = 10f //sp
                                text = "@" + qs.user.screenName
                                lines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            }.lparams {
                                leftMargin = dip(4)
                                rightOf(R.id.quoted_display_name)
                                baselineOf(R.id.quoted_display_name)
                            }

                            textView {
                                id = R.id.quoted_status
                                textSize = 12f //sp
                                text = qs.text
                                //tools:text = Hello World. //not support attribute
                            }.lparams {
                                below(R.id.quoted_display_name)
                            }

                            if (BasicSettings.displayThumbnailOn) {
                                frameLayout {
                                    id = R.id.quoted_images_container_wrapper

                                    val container = linearLayout {
                                        id = R.id.quoted_images_container
                                        orientation = LinearLayout.VERTICAL
                                    }

                                    val play = fontelloTextView {
                                        id = R.id.quoted_play
                                        text = resources.getString(R.string.fontello_play)
                                        textColor = Color.parseColor("#ffffff")
                                        textSize = 24f //sp
                                    }.lparams {
                                        gravity = Gravity.CENTER
                                    }

                                    qs.let {
                                        ImageUtil.displayThumbnailImages(mContext, container, this, play, it)
                                    }
                                }.lparams {
                                    below(R.id.quoted_status)
                                    bottomMargin = dip(4)
                                    topMargin = dip(10)
                                }
                            }
                            setOnClickListener {
                                startActivity<StatusActivity>("status" to qs.toJsonString())
                            }
                        }
                    }.lparams(width = matchParent) {
                        below(R.id.status)
                        rightOf(R.id.icon)
                        if (s.quotedStatus != null) {
                            topMargin = dip(10)
                            bottomMargin = dip(4)
                        }
                    }


                    frameLayout {
                        id = R.id.images_container_wrapper

                        if (BasicSettings.displayThumbnailOn) {
                            val container = linearLayout {
                                id = R.id.images_container
                                orientation = LinearLayout.VERTICAL
                            }

                            val play = fontelloTextView {
                                id = R.id.play
                                text = resources.getString(R.string.fontello_play)
                                textColor = Color.parseColor("#ffffff")
                                textSize = 24f //sp
                            }.lparams {
                                gravity = Gravity.CENTER
                            }

                            ImageUtil.displayThumbnailImages(mContext, container, this, play, s)
                        }
                    }.lparams {
                        below(R.id.quoted_tweet)
                        rightOf(R.id.icon)
                        bottomMargin = dip(4)
                        topMargin = dip(10)
                    }

                    relativeLayout {
                        id = R.id.menu_and_via_container

                        fontelloTextView {
                            id = R.id.do_reply
                            padding = dip(6)
                            text = resources.getString(R.string.fontello_reply)
                            textColor = Color.parseColor("#666666")
                            textSize = 14f
                            setOnClickListener {
                                ActionUtil.doReplyAll(s, mContext)
                            }
                            //tools:ignore = SpUsage //not support attribute
                        }

                        fontelloTextView {
                            id = R.id.do_retweet
                            topPadding = dip(6)
                            rightPadding = dip(4)
                            bottomPadding = dip(6)
                            leftPadding = dip(6)
                            text = resources.getString(R.string.fontello_retweet)
                            textColor = if (FavRetweetManager.getRtId(s) != null)
                                ContextCompat.getColor(mContext, R.color.holo_green_light)
                            else Color.parseColor("#666666")
                            textSize = 14f
                            setOnClickListener {
                                if (s.user.protected && s.user.id != currentIdentifier.userId) {
                                    MessageUtil.showToast(R.string.toast_protected_tweet_can_not_share)
                                    return@setOnClickListener
                                }

                                FavRetweetManager.getRtId(s)?.let { id ->
                                    if (id == 0L) {
                                        toast(R.string.toast_destroy_retweet_progress)
                                    } else {
                                        val dialog = DestroyRetweetDialogFragment().apply {
                                            arguments = Bundle(1).apply { putString("status", s.toJsonString()) }
                                        }
                                        EventBus.getDefault().post(AlertDialogEvent(dialog))
                                    }
                                } ?: run {
                                    val dialog = RetweetDialogFragment().apply {
                                        arguments = Bundle(1).apply {
                                            putString("status", s.toJsonString())
                                        }
                                    }
                                    EventBus.getDefault().post(AlertDialogEvent(dialog))
                                }
                            }
                        }.lparams {
                            rightOf(R.id.do_reply)
                            leftMargin = dip(22)
                        }

                        textView {
                            id = R.id.retweet_count
                            textSize = 10f
                            if (s.retweetCount > 0) {
                                bottomPadding = dip(6)
                                topPadding = dip(6)
                                textColor = Color.parseColor("#999999")
                                text = s.retweetCount.omitCount()
                            }
                        }.lparams(width = dip(32)) {
                            rightOf(R.id.do_retweet)
                        }

                        fontelloTextView {
                            id = R.id.do_fav
                            topPadding = dip(6)
                            rightPadding = dip(4)
                            bottomPadding = dip(6)
                            leftPadding = dip(2)
                            text = resources.getString(R.string.fontello_star)
                            if (FavRetweetManager.isFav(s)) {
                                tag = "is_fav"
                                textColor = ContextCompat.getColor(mContext, R.color.holo_orange_light)
                            } else {
                                tag = "no_fav"
                                textColor = Color.parseColor("#666666")
                            }
                            textSize = 14f
                            setOnClickListener {
                                if (tag == "is_fav") {
                                    tag = "no_fav"
                                    textColor = Color.parseColor("#666666")
                                    GlobalScope.launch(Dispatchers.Main) { ActionUtil.doDestroyFavorite(s.id) }
                                } else {
                                    tag = "is_fav"
                                    textColor = ContextCompat.getColor(mContext, R.color.holo_orange_light)
                                    GlobalScope.launch(Dispatchers.Main) { ActionUtil.doFavorite(s.id) }
                                }
                            }
                        }.lparams {
                            rightOf(R.id.retweet_count)
                        }

                        textView {
                            id = R.id.fav_count
                            textSize = 10f
                            if (s.favoriteCount > 0) {
                                bottomPadding = dip(6)
                                topPadding = dip(6)
                                textColor = Color.parseColor("#999999")
                                text = s.favoriteCount.omitCount()
                            }
                        }.lparams {
                            rightOf(R.id.do_fav)
                        }

                        textView {
                            id = R.id.via
                            bottomPadding = dip(2)
                            textColor = Color.parseColor("#666666")
                            textSize = 8f //sp
                            text = "via ${s.via.name}"
                        }.lparams {
                            alignParentRight()
                        }

                        textView {
                            id = R.id.datetime
                            textColor = Color.parseColor("#666666")
                            textSize = 10f //sp
                            text = TimeUtil.getAbsoluteTime(s.createdAt.date)
                        }.lparams {
                            below(R.id.via)
                            alignParentRight()
                        }
                    }.lparams {
                        below(R.id.images_container_wrapper)
                        rightOf(R.id.icon)
                    }

                    if (status.retweetedStatus != null) {
                        relativeLayout {
                            id = R.id.retweet_container

                            imageView {
                                id = R.id.retweet_icon
                                contentDescription = resources.getString(R.string.description_icon)
                                //UserIconManager.displayUserIcon(status.user, this)
                            }.lparams(width = dip(18), height = dip(18)) {
                                rightMargin = dip(4)
                            }

                            textView {
                                id = R.id.retweet_by
                                textSize = 10f //sp
                                text = "RT by ${status.user.name} @ ${status.user.screenName}"
                            }.lparams {
                                rightOf(R.id.retweet_icon)
                                topMargin = dip(2)
                            }

                        }.lparams {
                            rightOf(R.id.icon)
                            below(R.id.menu_and_via_container)
                            bottomMargin = dip(2)
                        }
                    }

                    if (StatusUtil.isMentionForMe(s).not() && s.retweetedStatus == null) actionContainer.visibility = View.GONE
                }
            }
        } ?: relativeLayout()
    }

    private inner class RowData(val textId: Int, val textColor: Int, val displayName: String, screenNameParam: String) {
        val screenName: String = "@$screenNameParam"
    }
}