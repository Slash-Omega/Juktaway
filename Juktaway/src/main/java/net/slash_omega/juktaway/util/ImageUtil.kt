package net.slash_omega.juktaway.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer
import jp.nephy.jsonkt.stringify
import jp.nephy.penicillin.endpoints.media.MediaType
import jp.nephy.penicillin.models.Status
import net.slash_omega.juktaway.ScaleImageActivity
import net.slash_omega.juktaway.VideoActivity
import net.slash_omega.juktaway.app
import net.slash_omega.juktaway.settings.preferences
import org.jetbrains.anko.startActivity
import java.io.File

/**
 * Created on 2018/10/25.
 */

fun ImageView.displayImage(url: String) {
    if ((tag as? String) == url) return
    tag = url
    ImageLoader.getInstance().displayImage(url, this)
}

fun ImageView.displayImage(url: Uri) {
    displayImage(url.toString())
}

fun ImageView.displayRoundedImage(url: String) {
    if ((tag as? String) == url) return
    tag = url
    if (preferences.display.tweet.isAuthorIconRounded) {
        ImageLoader.getInstance().displayImage(url, this, ImageUtil.sRoundedDisplayImageOptions)
    } else {
        ImageLoader.getInstance().displayImage(url, this)
    }
}

fun File.mediaType() = ImageUtil.toMediaType(path.run { substring(lastIndexOf(".") + 1) })

object ImageUtil {
    private val imageLoader = ImageLoader.getInstance()

    internal val sRoundedDisplayImageOptions by lazy {
        DisplayImageOptions.Builder()
                .cacheInMemory(true)
                .resetViewBeforeLoading(true)
                .displayer(FadeInBitmapDisplayer(5))
                .build()
    }

    init {
        val defaultOptions = DisplayImageOptions.Builder()
                .cacheInMemory(true)
                .resetViewBeforeLoading(true)
                .build()

        imageLoader.init(
                ImageLoaderConfiguration.Builder(app)
                        .defaultDisplayImageOptions(defaultOptions)
                        .build())
    }

    fun displayImage(url: String, view: ImageView) {
        val tag = view.tag as? String
        if (tag != null && tag == url) return
        view.tag = url
        ImageLoader.getInstance().displayImage(url, view)
    }

    fun displayRoundedImage(url: String, view: ImageView) {
        val tag = view.tag as? String
        if (tag != null && tag == url) return
        view.tag = url
        if (preferences.display.tweet.isAuthorIconRounded) {
            imageLoader.displayImage(url, view, sRoundedDisplayImageOptions)
        } else {
            imageLoader.displayImage(url, view)
        }
    }

    fun displayThumbnailImages(context: Context, group: ViewGroup, wrapperGroup: ViewGroup, play: View, status: Status) {
        status.imageUrls.takeNotEmpty()?.let { imageUrls ->
            group.removeAllViews()
            imageUrls.forEachIndexed { i, url ->
                val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                group.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 240).apply {
                    if (i > 0) setMargins(0, 20, 0, 0)
                })
                displayRoundedImage(url, image)

                image.setOnClickListener {
                    if (status.videoUrl.isEmpty()) {
                        context.startActivity(Intent(it.context, ScaleImageActivity::class.java).apply {
                            putExtra("status", status.stringify())
                            putExtra("index", i)
                        })
                    } else {
                        context.startActivity<VideoActivity>("arg" to "statusJson", "statusJson" to status.stringify())
                    }
                }
            }
            group.visibility = View.VISIBLE
            wrapperGroup.visibility = View.VISIBLE
        } ?: run {
            group.visibility = View.GONE
            wrapperGroup.visibility = View.GONE
        }
        play.visibility = if (status.videoUrl.isEmpty()) View.GONE else View.VISIBLE
    }

    fun toMediaType(format: String) = when(format.toLowerCase()) {
        "png" -> MediaType.PNG
        "gif" -> MediaType.GIF
        "webp" -> MediaType.WebP
        else -> MediaType.JPEG
    }
}
