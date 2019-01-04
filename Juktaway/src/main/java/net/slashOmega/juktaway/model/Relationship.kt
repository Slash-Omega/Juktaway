package net.slashOmega.juktaway.model

import jp.nephy.penicillin.PenicillinClient
import jp.nephy.penicillin.models.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.slashOmega.juktaway.twitter.identifierList

object Relationship {
    private val blockList = mutableListOf<Long>()
    private val officialMuteList = mutableListOf<Long>()
    private val noRetweetList = mutableListOf<Long>()
    private val myIdList = mutableListOf<Long>()

    init {
        GlobalScope.launch(Dispatchers.Default) {
            val identifiers = identifierList
            myIdList.addAll(identifiers.map { it.userId })
            identifiers.forEach {
                it.asClient {
                    loadBlock(this)
                    loadOfficialMute(this)
                    loadNoRetweet(this)
                }
            }
        }
    }

    fun isMe(userId: Long): Boolean = userId in myIdList

    fun isBlock(userId: Long): Boolean = userId in blockList

    fun isOfficialMute(userId: Long): Boolean = userId in officialMuteList

    fun isNoRetweet(userId: Long): Boolean = userId in noRetweetList


    fun setBlock(userId: Long) { blockList.add(userId) }

    fun setOfficialMute(userId: Long) { officialMuteList.add(userId) }

    fun setNoRetweet(userId: Long) { noRetweetList.add(userId) }

    fun removeBlock(userId: Long) { blockList.remove(userId) }

    fun removeOfficialMute(userId: Long) { officialMuteList.remove(userId) }

    fun removeNoRetweet(userId: Long) { noRetweetList.remove(userId) }

    private suspend fun loadBlock(client: PenicillinClient) {
        runCatching { client.block.listIds().await().result.ids }
                .getOrNull()?.let { blockList.addAll(it) }
    }

    private suspend fun loadOfficialMute(client: PenicillinClient) {
        runCatching { client.mute.listIds().await().result.ids }
                .getOrNull()?.let { officialMuteList.addAll(it) }
    }

    private suspend fun loadNoRetweet(client : PenicillinClient) {
        runCatching { client.friendship.noRetweetsIds().await().result.ids }
                .getOrNull()?.let { noRetweetList.addAll(it) }
    }
    private fun isThatStatusVisible(id: Long): Boolean = isMe(id) || !(isBlock(id) || isOfficialMute(id))

    fun isVisible(status: Status) = isThatStatusVisible(status.user.id)
            && status.retweetedStatus?.let { isThatStatusVisible(status.user.id) && !isNoRetweet(status.user.id) } ?: true
            && status.quotedStatus?.let { isThatStatusVisible(status.user.id) } ?: true
}
