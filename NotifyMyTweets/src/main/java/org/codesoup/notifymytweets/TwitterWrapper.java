package org.codesoup.notifymytweets;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.Serializable;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.UserStream;
import twitter4j.UserStreamAdapter;
import twitter4j.auth.AccessToken;
import twitter4j.auth.Authorization;
import twitter4j.auth.NullAuthorization;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;

import static android.os.SystemClock.sleep;

class TwitterWrapper implements Serializable, Handler.Callback, TwitterAuthentication, TwitterClient {
    private static final String TAG = "TwitterWrapper";
    private final Configuration mConfiguration;
    private final String mConsumerKey;
    private final String mConsumerSecret;
    private Authorization mAuthorization;
    private String mOAuthVerifier;

    private transient Twitter mTwitter;
    private transient TwitterStream mTwitterStream;

    private RequestToken mRequestToken;
    private String mCallbackUri;

    private transient String mAuthorizationUrl; // transient because it can only be used once
    private AccessToken mOAuthAccessToken;

    private final int GET_LAST_TWEET = 0;
    private final int START_STREAM = 1;

    public TwitterWrapper(String consumerKey, String consumerSecret) {
        mConsumerKey = consumerKey;
        mConsumerSecret = consumerSecret;
        mTwitter = new TwitterFactory().getInstance();
        mTwitter.setOAuthConsumer(consumerKey, consumerSecret);
        mConfiguration = mTwitter.getConfiguration();
    }

    private Twitter getTwitter() {
        if (mTwitter == null) {
            // may be necessary if instance was serialized
            mTwitter = new TwitterFactory(mConfiguration).getInstance(mAuthorization);
            if (mTwitter.getAuthorization() == null || mTwitter.getAuthorization() instanceof NullAuthorization)
                mTwitter.setOAuthConsumer(mConsumerKey, mConsumerSecret);
        }
        return mTwitter;
    }

    private TwitterStream getTwitterStream() {
        if (mTwitterStream == null) {
            mTwitterStream = new TwitterStreamFactory(mConfiguration).getInstance(mAuthorization);
        }
        return mTwitterStream;
    }

    private transient Handler threadHandler;
    private transient HandlerThread mHandlerThread;

    private Handler getThreadHandler() {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("Network");
            mHandlerThread.start();
            threadHandler = new Handler(mHandlerThread.getLooper(), this);
        }
        return threadHandler;
    }

    @Override
    public Uri getAuthorizationUrl() throws TwitterException {
        if (mAuthorizationUrl == null) {
            RequestToken requestToken = getTwitter().getOAuthRequestToken(mCallbackUri);
            mAuthorizationUrl = requestToken.getAuthenticationURL();
            mRequestToken = requestToken;
        }
        return Uri.parse(mAuthorizationUrl);
    }

    @Override
    public void setOAuthVerifier(String OAuthVerifier) {
        mOAuthVerifier = OAuthVerifier;
    }

    // FIXME -- workflow is quite not clear : onReady must be called as soon as we have an access token.
    // - when starting from a blank state, several OAuth steps are required to make the access token available
    // - the access token can be directly present after deserialization, in which case it can start immediately
    // Some kind of trigger should help avoiding repetition in very different places.

    @Override
    public AccessToken getOAuthAccessToken() throws TwitterException {
        if (mOAuthAccessToken == null) {
            mOAuthAccessToken = getTwitter().getOAuthAccessToken(mRequestToken, mOAuthVerifier);
            mAuthorization = getTwitter().getAuthorization();
        }

        if (mListener != null) {
            mListener.onReady(this);
        }

        return mOAuthAccessToken;
    }

    private transient NewTweetListener mListener;

    @Override
    public void setNewTweetListener(NewTweetListener listener) {
        mListener = listener;
        if (isAuthorized()) {
            mListener.onReady(this);
        }
    }

    @Override
    public boolean isAuthorized() {
        return getTwitter().getAuthorization().isEnabled();
    }

    @Override
    public void setCallbackUri(String callbackUri) {
        mCallbackUri = callbackUri;
    }

    private long mLastTweetId = 0;

    private Status getLastTweet() throws TwitterException {
        ResponseList<Status> timeline = getTwitter().getUserTimeline(new Paging(1, 1));
        if (timeline.size() > 0) {
            return timeline.get(0);
        }
        return null;
    }

    private void onTweet(Status tweet) {
        Log.i(TAG, String.format("%d %d", tweet.getId(), mLastTweetId));
        if (tweet.getId() > mLastTweetId) {
            mLastTweetId = tweet.getId();
            if (mListener != null) {
                mListener.onNewTweet(tweet);
            }
        }
    }

    @Override
    public void startStreamer() {
        // TODO -- should be the streamer but we just start the getLastTweet for now
        Message.obtain(getThreadHandler(), START_STREAM).sendToTarget();
    }

    private void handleGetLastTweet() {
        try {
            Status lastTweet = getLastTweet();
            if (lastTweet != null) {
                onTweet(lastTweet);
            }
        } catch (TwitterException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case GET_LAST_TWEET:
                handleGetLastTweet();
                return true;
            case START_STREAM:
                handleGetLastTweet(); // always start by getting last tweet, as we won't get old messages
                getTwitterStream().addListener(new UserStreamAdapter() {
                    @Override
                    public void onStatus(Status status) {
                        onTweet(status);
                    }
                });
                getTwitterStream().user();
                return true;
        }
        return false;
    }
}