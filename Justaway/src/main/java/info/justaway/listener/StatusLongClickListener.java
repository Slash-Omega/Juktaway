package info.justaway.listener;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;

import info.justaway.TwitterAction;
import info.justaway.adapter.TwitterAdapter;
import info.justaway.fragment.AroundFragment;
import info.justaway.fragment.TalkFragment;
import info.justaway.settings.BasicSettings;
import twitter4j.Status;

public class StatusLongClickListener implements AdapterView.OnItemLongClickListener {

    private TwitterAdapter mAdapter;
    private FragmentActivity mActivity;

    public StatusLongClickListener(TwitterAdapter adapter, Activity activity) {
        mAdapter = adapter;
        mActivity = (FragmentActivity) activity;
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        Bundle args = new Bundle();
        String action = BasicSettings.getLongTapAction();

        if (mAdapter.getItem(position).isDirectMessage()) {
            return false;
        }

        Status status = mAdapter.getItem(position).getStatus();
        final Status retweet = status.getRetweetedStatus();
        final Status source = retweet != null ? retweet : status;

        if (action.equals("quote")) {
            TwitterAction.doQuote(source, mActivity);
        } else if (action.equals("talk")) {
            if (source.getInReplyToStatusId() > 0) {
                TalkFragment dialog = new TalkFragment();
                args.putSerializable("status", source);
                dialog.setArguments(args);
                dialog.show(mActivity.getSupportFragmentManager(), "dialog");
            } else {
                return false;
            }
        } else if (action.equals("show_around")) {
            AroundFragment aroundFragment = new AroundFragment();
            Bundle aroundArgs = new Bundle();
            aroundArgs.putSerializable("status", source);
            aroundFragment.setArguments(aroundArgs);
            aroundFragment.show(mActivity.getSupportFragmentManager(), "dialog");
        } else if (action.equals("share_url")) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, "https://twitter.com/" + status.getUser().getScreenName()
                    + "/status/" + String.valueOf(status.getId()));
            mActivity.startActivity(intent);
        } else if (action.equals("reply_all")) {
            TwitterAction.doReplyAll(source, mActivity);
        } else {
            return false;
        }
        return true;
    }
}
