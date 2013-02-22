package com.simperium.client;

import com.loopj.android.http.*;
import com.loopj.android.http.JsonHttpResponseHandler;

import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class Simperium implements User.AuthenticationListener {

    public static final String HTTP_USER_AGENT = "android-1.0";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    private String appId;
    private String appSecret;

    public static final String TAG = "Simperium";
    protected AsyncHttpClient httpClient;
    protected AuthHttpClient authClient;
    protected WebSocketManager socketManager;
    
    private User user;
    private Context context;
    private User.AuthenticationListener authenticationListener;
    private StorageProvider storageProvider;
    
    public Simperium(String appId, String appSecret, Context context, StorageProvider storageProvider){
        this(appId, appSecret, context, storageProvider, null);
    }
    
    public Simperium(String appId, String appSecret, Context context, StorageProvider storageProvider, User.AuthenticationListener authenticationListener){
        this.appId = appId;
        this.appSecret = appSecret;
        this.context = context;
        httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(HTTP_USER_AGENT);
        authClient = new AuthHttpClient(appId, appSecret, httpClient);
        socketManager = new WebSocketManager(appId);
        this.authenticationListener = authenticationListener;
        this.storageProvider = storageProvider;
        loadUser();
    }
    
    private void loadUser(){
        user = new User(this);
        String token = getUserAccessToken();
        if (token != null) {
            user.setAccessToken(token);
            user.setAuthenticationStatus(User.AuthenticationStatus.AUTHENTICATED);
        } else {
            user.setAuthenticationStatus(User.AuthenticationStatus.NOT_AUTHENTICATED);
        }
    }
    
    public String getAppId(){
        return appId;
    }
    
    public User getUser(){
        return user;
    }
    
    public boolean needsAuthentication(){
        // we don't have an access token yet
        return user.needsAuthentication();
    }
    
    /**
     * Creates a bucket and starts syncing data and uses the provided
     * Class for to instantiate data
     *
     * @param bucketName the namespace to store the data in simperium
     */
    public <T extends Bucket.Diffable> Bucket<T> bucket(String bucketName, Bucket.EntityFactory<T> factory){
        // TODO: cache the bucket by user and bucketName and return the
        // same bucket if asked for again
        Bucket<T> bucket = new Bucket<T>(bucketName, factory, user, storageProvider);
        Channel<T> channel = socketManager.createChannel(bucket, user);
        bucket.setChannel(channel);
        return bucket;
    }
    /**
     * Creates a bucket and starts syncing data. Users the generic BucketObject for
     * serializing and deserializing data
     *
     * @param bucketName namespace to store the data in simperium
     */
    public Bucket<Entity> bucket(String bucketName){
        return bucket(bucketName, new Entity.Factory());
    }
        
    public User createUser(String email, String password, User.AuthResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.createUser(user, handler);
    }
    
    public User authorizeUser(String email, String password, User.AuthResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.authorizeUser(user, handler);
    }
    
    public static final void log(String msg){
        Log.d(TAG, msg);
    }
    
    public static final void log(String tag, String msg){
        Log.d(tag, msg);
    }
    
    public static final void log(String msg, Throwable error){
        Log.e(TAG, msg, error);
    }
    
    public void onAuthenticationStatusChange(User.AuthenticationStatus status){

        switch (status) {
            case AUTHENTICATED:
            // Start up the websocket
            // save the key
            saveUserAccessToken();
            socketManager.connect();
            break;
            case NOT_AUTHENTICATED:
            clearUserAccessToken();
            // Disconnect the websocket
            socketManager.disconnect();
            break;
            case UNKNOWN:
            // we haven't tried to auth yet or the socket is disconnected
            break;
        }
        if (authenticationListener != null) {
            authenticationListener.onAuthenticationStatusChange(status);
        }
    }
    
    private SharedPreferences.Editor getPreferenceEditor(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.edit();
    }
    
    private boolean clearUserAccessToken(){
        SharedPreferences.Editor editor = getPreferenceEditor();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        return editor.commit();
    }
    
    private boolean saveUserAccessToken(){
        String token = user.getAccessToken();
        SharedPreferences.Editor editor = getPreferenceEditor();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, token);
        return editor.commit();
    }
    
    private String getUserAccessToken(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String token = preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null);
        return token;
    }

                
}
