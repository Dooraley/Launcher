package pro.gravit.launcher.request.uuid;

import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.events.request.BatchProfileByUsernameRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.websockets.WebSocketRequest;
import pro.gravit.launcher.serialize.SerializeLimits;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.VerifyHelper;

import java.io.IOException;

public final class BatchProfileByUsernameRequest extends Request<BatchProfileByUsernameRequestEvent> implements WebSocketRequest {
    @LauncherNetworkAPI
    private final Entry[] list;

    public BatchProfileByUsernameRequest(String... usernames) throws IOException {
        this.list = new Entry[usernames.length];
        for (int i = 0; i < usernames.length; ++i) {
            this.list[i].client = "";
            this.list[i].username = usernames[i];
        }
        IOHelper.verifyLength(usernames.length, SerializeLimits.MAX_BATCH_SIZE);
        for (String username : usernames)
            VerifyHelper.verifyUsername(username);
    }

    @Override
    public String getType() {
        return "batchProfileByUsername";
    }

    static class Entry {
        @LauncherNetworkAPI
        String username;
        @LauncherNetworkAPI
        String client;
    }
}
