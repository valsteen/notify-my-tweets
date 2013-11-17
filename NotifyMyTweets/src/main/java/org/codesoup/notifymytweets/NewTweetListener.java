package org.codesoup.notifymytweets;


import twitter4j.Status;

public interface NewTweetListener {
    public void onNewTweet(Status tweet);
    public void onReady(TwitterClient twitter) ;
}
