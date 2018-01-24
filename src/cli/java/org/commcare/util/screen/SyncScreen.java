package org.commcare.util.screen;

import org.commcare.modern.session.SessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.PostRequest;
import org.commcare.suite.model.RemoteRequestEntry;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Screen to make a sync request to HQ after a case claim. Unlike other all other screens,
 * SyncScreen does not take input - it simply processes the PostRequest object, makes the request,
 * and displays the result (if necessary)
 */
public class SyncScreen extends Screen {

    protected SessionWrapper sessionWrapper;
    private String username;
    private String password;
    private PrintStream printStream;
    private boolean syncSuccessful;

    public SyncScreen(String username, String password, PrintStream printStream) {
        super();
        this.username = username;
        this.password = password;
        this.printStream = printStream;
    }

    @Override
    public void init (SessionWrapper sessionWrapper) throws CommCareSessionException {
        this.sessionWrapper = sessionWrapper;
        parseMakeRequest();
    }

    private void parseMakeRequest() throws CommCareSessionException {
        String command = sessionWrapper.getCommand();
        Entry commandEntry = sessionWrapper.getPlatform().getEntry(command);
        if (commandEntry instanceof RemoteRequestEntry) {
            PostRequest syncPost = ((RemoteRequestEntry)commandEntry).getPostRequest();
            Response response = makeSyncRequest(syncPost);
            if (response == null || !response.isSuccessful()) {
                printStream.println(String.format("Sync failed with response %s", response));
                printStream.println("Press 'enter' to retry.");
                return;
            }
            syncSuccessful = true;
            printStream.println(String.format("Sync successful with response %s", response));
            printStream.println("Press 'enter' to continue.");
        } else {
            // expected a sync entry; clear session and show vague 'session error' message to user
            throw new CommCareSessionException("Initialized sync request while not on sync screen");
        }
    }

    private static String buildUrl(String baseUrl) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        return urlBuilder.build().toString();
    }

    private static MultipartBody buildPostBody(Hashtable<String, String> params) {
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);
        // Add buffer param since this is necessary for some reason
        requestBodyBuilder.addFormDataPart("buffer", "buffer");
        for (String key: params.keySet()) {
            requestBodyBuilder.addFormDataPart(key, params.get(key));
        }
        return requestBodyBuilder.build();
    }

    private Response makeSyncRequest(PostRequest syncPost) throws CommCareSessionException {
        Hashtable<String, String> params = syncPost.getEvaluatedParams(sessionWrapper.getEvaluationContext());
        String url = buildUrl(syncPost.getUrl().toString());
        printStream.println(String.format("Syncing with url %s and parameters %s", url, params));
        MultipartBody postBody = buildPostBody(params);
        String credential = Credentials.basic(username, password);

        Request request = new Request.Builder()
                .url(url)
                .method("POST", RequestBody.create(null, new byte[0]))
                .header("Authorization", credential)
                .post(postBody)
                .build();
        try {
            OkHttpClient client = new OkHttpClient();
            return client.newCall(request).execute();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void prompt(PrintStream printStream) throws CommCareSessionException {
        if (syncSuccessful) {
            printStream.println("Sync complete, press Enter to continue");
        } else {
            printStream.println("Sync failed, press Enter to continue");
        }
    }

    @Override
    public boolean handleInputAndUpdateSession(CommCareSession commCareSession, String s) throws CommCareSessionException {
        if (syncSuccessful) {
            commCareSession.syncState();
            if (commCareSession.finishExecuteAndPop(sessionWrapper.getEvaluationContext())) {
                sessionWrapper.clearVolatiles();
            }
            return false;
        } else {
            parseMakeRequest();
            return true;
        }
    }

    @Override
    public String[] getOptions() {
        return new String[0];
    }
}
