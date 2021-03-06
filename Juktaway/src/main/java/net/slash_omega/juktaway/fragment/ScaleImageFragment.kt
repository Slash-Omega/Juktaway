package net.slash_omega.juktaway.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nostra13.universalimageloader.core.ImageLoader
import net.slash_omega.juktaway.widget.ScaleImageView

class ScaleImageFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = activity?.let { activity ->
        ScaleImageView(activity).apply {
            mActivity = this@ScaleImageFragment.activity
            arguments?.getString("url")?.let {
                ImageLoader.getInstance().displayImage(it, this)
            }
        }
    }
}
