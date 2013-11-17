package org.codesoup.notifymytweets;

import android.net.Uri;

import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;

public interface TwitterAuthentication {
    Uri getAuthorizationUrl() throws TwitterException;

    void setOAuthVerifier(String OAuthVerifier);

    AccessToken getOAuthAccessToken() throws TwitterException;

    void setNewTweetListener(NewTweetListener listener);

    boolean isAuthorized();

    void setCallbackUri(String callbackUri);
}
