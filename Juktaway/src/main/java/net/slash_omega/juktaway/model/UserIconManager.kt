package net.slash_omega.juktaway.model

import android.widget.ImageView
import jp.nephy.penicillin.endpoints.users
import jp.nephy.penicillin.endpoints.users.lookupByIds
import jp.nephy.penicillin.extensions.await
import jp.nephy.penicillin.extensions.models.ProfileImageSize
import jp.nephy.penicillin.extensions.models.profileImageUrlHttpsWithVariantSize
import jp.nephy.penicillin.models.CommonUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.slash_omega.juktaway.settings.Preferences.DisplayPreferences.PictureQuality.*
import net.slash_omega.juktaway.settings.preferences
import net.slash_omega.juktaway.twitter.currentClient
import net.slash_omega.juktaway.twitter.isIdentifierSet
import net.slash_omega.juktaway.util.ImageUtil
import net.slash_omega.juktaway.util.JuktawayDBOpenHelper.Companion.dbUse
import net.slash_omega.juktaway.util.tryAndTraceGet
import org.jetbrains.anko.db.*

/**
 * Created on 2018/11/01.
 */

fun ImageView.displayUserIcon(user: CommonUser) {
    val url = user.profileImageUrlHttpsWithVariantSize( when (preferences.display.pictureQuality) {
        HIGH -> ProfileImageSize.Bigger
        else -> ProfileImageSize.Mini
    })

    if (preferences.display.tweet.isAuthorIconRounded) {
        ImageUtil.displayImage(url, this)
    } else {
        ImageUtil.displayRoundedImage(url, this)
    }
}

object UserIconManager {
    private const val tableName = "userIcon"

    init { dbUse {
        createTable(tableName, true,
                "userId" to INTEGER + PRIMARY_KEY,
                "iconUrl" to TEXT + NOT_NULL,
                "name" to TEXT + NOT_NULL)
    }}

    fun ImageView.displayUserIcon(userId: Long) {
        val url = dbUse {
            select(tableName, "iconUrl")
                    .whereArgs("(userId) = {id}", "id" to userId)
                    .parseSingle(StringParser)
        }
        ImageUtil.displayRoundedImage(url, this)
    }

    suspend fun getName(userId: Long): String = withContext(Dispatchers.Default) {
        dbUse {
            select(tableName, "name")
                    .whereArgs("(userId = {id})", "id" to userId)
                    .parseSingle(StringParser)
        }
    }

    suspend fun addUserIconMap(user: CommonUser) {
        withContext(Dispatchers.Default) {
            dbUse {
                runCatching {
                    select(tableName, "userId")
                            .whereArgs("userId = {id}", "id" to user.id)
                            .parseSingle(LongParser)
                }.onFailure {
                    insert(tableName, "userId" to user.id, "iconUrl" to user.profileImageUrl, "name" to user.name)
                }
            }
        }
    }

    fun warmUpUserIconMap() {
        if (!isIdentifierSet) return
        GlobalScope.launch(Dispatchers.Main) {
            val data = dbUse { select(tableName, "userId").parseList(LongParser) }
            if (data.isNullOrEmpty()) return@launch
            tryAndTraceGet {
                val users = currentClient.users.lookupByIds(data).await()
                dbUse {
                    users.forEach { u ->
                        update(tableName,
                                "iconUrl" to u.profileImageUrlHttpsWithVariantSize(ProfileImageSize.Bigger),
                                "name" to u.name)
                                .whereArgs("(userId = {userId})", "userId" to u.id)
                                .exec()
                    }
                }
            }
        }
    }
}