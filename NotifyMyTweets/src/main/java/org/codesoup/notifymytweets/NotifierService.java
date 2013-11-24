package org.codesoup.notifymytweets;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import twitter4j.Status;
import twitter4j.User;

public class NotifierService extends Service implements NewTweetListener {
    private static final String TAG = "NotifierService";

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onNewTweet(Status tweet) {
        showNotification(tweet.getUser());
    }

    @Override
    public void onReady(TwitterClient twitter) {
        twitter.startStreamer();
    }

    class NotifierBinder extends Binder {
        NotifierService getService() {
            return NotifierService.this;
        }
    }

    private final IBinder binder = new NotifierBinder();

    private Intent getTwitterIntent(User user) {
        try {
            // Check if the Twitter app is installed on the phone.
            getPackageManager().getPackageInfo("com.twitter.android", 0);
            return new Intent(Intent.ACTION_VIEW, Uri.parse("twitter://user?screen_name=" + user.getScreenName()));
        } catch (PackageManager.NameNotFoundException e) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(String.format("https://twitter.com/%s/", user.getScreenName())));
        }
    }

    private int notificationId = 1;

    private void showNotification(User user) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(String.format("New tweet from %s", user.getScreenName()))
                        .setLights(0xFFFF0000, 500, 500)
                        .setVibrate(new long[] {0, 100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100, 100})
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setAutoCancel(true);

        // Creates an explicit intent for an Activity in your app
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(getTwitterIntent(user));

        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(notificationId, mBuilder.build());
    }
}