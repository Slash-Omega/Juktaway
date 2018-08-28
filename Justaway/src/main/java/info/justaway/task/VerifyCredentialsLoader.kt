package info.justaway.task

import android.content.Context

import info.justaway.model.TwitterManager
import twitter4j.TwitterException
import twitter4j.User

class VerifyCredentialsLoader(context: Context) : AbstractAsyncTaskLoader<User>(context) {

    override fun loadInBackground(): User? {
        return try {
            TwitterManager.getTwitter().verifyCredentials()
        } catch (e: TwitterException) {
            e.printStackTrace()
            null
        }

    }
}