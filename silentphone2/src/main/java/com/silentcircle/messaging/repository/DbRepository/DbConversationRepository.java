/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.silentcircle.messaging.repository.DbRepository;

import android.content.Context;
import android.util.Log;

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.json.JSONConversationAdapter;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.util.CryptoUtil;
import com.silentcircle.messaging.util.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import axolotl.AxolotlNative;

public class DbConversationRepository /*extends BaseFileRepository<Conversation>*/ implements ConversationRepository {

    private static final String TAG = "DbConvRepository";
    private final JSONConversationAdapter adapter = new JSONConversationAdapter();
    private final Map<byte[], EventRepository> histories = new HashMap<>();

    public static final String NAME_SEPARATOR = "_:::_";

    private static final List<Conversation> EMPTY_CONVERSATION_LIST = Arrays.asList(new Conversation[0]);
    private static final String ownRepoNameSuffix = "$$_REPO_FOR_ME_$$";
    private final String userName;
    private final String userNameSep;
    private final Context context;

    /**
     * Get a Conversation repository for a local user.
     * <p/>
     * The client may handle several local accounts with different user names. The DB repository
     * combines the local username and a partner's name to create a unique key to identity a conversation:
     * <p/>
     * {@code <local username>_:::_<partner's name>}
     * <p/>
     * where the sequence {@code _:::_} is just a separator between the two names
     *
     * @param username The name of the local user
     * @param key      The key used to encrypt data
     */
    public DbConversationRepository(Context ctx, String name) {
        context = ctx;
        userName = name;
        userNameSep = name + NAME_SEPARATOR;
        if (!exists(ownRepoNameSuffix)) {
            byte[] id = IOUtils.encode(userNameSep + "$$_REPO_FOR_ME_$$");
            byte[] data = {0x0d, 0x0e, 0x0a, 0x0d, 0x0c, 0x0a, 0x0f, 0x0e};
            AxolotlNative.storeConversation(id, data);
        }
    }


//	@Override
//	public ResourceStateRepository contextOf( Conversation conversation ) {
//		if( conversation == null ) {
//			return null;
//		}
//		String id = identify( conversation );
//		ResourceStateRepository repository = contexts.get( id );
//		if( repository == null ) {
//			repository = new FileResourceStateRepository( new File( root, Hash.sha1( id + ".contexts" ) ) );
//			( (FileResourceStateRepository) repository ).setFilter( filter );
//			contexts.put( id, repository );
//		}
//		return repository;
//	}

    @Override
    public void clear() {
        List<Conversation> conversations = list();
        for (Conversation conv: conversations) {
            remove(conv);
        }
    }

    /**
     * Check if a Conversation repository exists for the local user.
     *
     * @return {@code true} if the repository exists, {@code false} if not.
     */
    @Override
    public boolean exists() {
        return exists(ownRepoNameSuffix);
    }

    /**
     * Check if a Conversation for a partner already exists.
     *
     * @return {@code true} if a conversation exists, {@code false} if not.
     */
    @Override
    public boolean exists(String partner) {
        byte[] id = IOUtils.encode(userNameSep + partner);
        return AxolotlNative.existConversation(id);
    }

    /**
     * Read conversation data from repository and return an Conversation object.
     *
     * @param partner The conversation partner's name
     * @return the conversation object or {@code null} if not found
     */
    @Override
    public Conversation findById(String partner) {
        byte[] id = IOUtils.encode(userNameSep + partner);
        int[] code = new int[1];
        byte[] data = AxolotlNative.loadConversation(id, code);
        if (data != null) {
            return deserialize(new String(data));
        }
        return null;
    }

    /**
     * List of known conversations for this user, based on known users of Axolotl sessions and
     * saved conversations.
     *
     * @return List of conversations or EMPTY_CONVERSATION_LIST if no conversations found.
     */
    @Override
    public List<Conversation> list() {
        Set<String> conversationPartners = new HashSet<>();
        for (byte[] conversation : AxolotlNative.listConversations()) {
            try {
                /*
                 * avoid conversations which are not my own (starting with something else than
                 * my username
                 */
                String conversationId = new String(conversation);
                String[] names = conversationId.split(NAME_SEPARATOR);
                if (names.length >= 2 && names[0].equals(userName)) {
                    conversationPartners.add(names[1]);
                }
            } catch (Exception e) {
                // ignore conversation for which it is not possible to determine partner
            }
        }
        conversationPartners.remove(ownRepoNameSuffix);

        byte[] usersJson = AxolotlNative.getKnownUsers();
        if ((usersJson != null && usersJson.length > 0)) {
            List<String> partners = parseKnownUsers(new String(usersJson));
            conversationPartners.addAll(partners);
        }
        if (conversationPartners.size() == 0)
            return EMPTY_CONVERSATION_LIST;

        List<Conversation> conversations = new ArrayList<>(conversationPartners.size());
        for (String partner : conversationPartners) {
            conversations.add(findById(partner));
        }
        return conversations;
    }

    private Conversation deserialize(String serial) {
        if (serial == null) {
            return null;
        }
        try {
            return adapter.adapt(new JSONObject(serial));
        } catch (JSONException exception) {
            return null;
        }
    }

    @Override
    public Conversation findByPartner(String partner) {
        return findById(partner);
    }

    @Override
    public EventRepository historyOf(Conversation conversation) {
        // TODO if DbEventRepository is ready
        if (conversation == null) {
            return null;
        }
        byte[] id = IOUtils.encode(userNameSep + identify(conversation));
        EventRepository history = histories.get(id);
        if (history == null) {
			history = new DbEventRepository(context, id);
//			histories.put( id, history );
        }
        return history;
    }

    private String identify(Conversation conversation) {
        return conversation == null ? null : conversation.getPartner() == null ? null : conversation.getPartner().getUsername();
    }

    public void remove(Conversation conversation) {
        if (conversation == null) {
            return;
        }
        // TODO: check if we need context in Axolotl, context maybe SCIMP specific: contextOf( conversation ).clear();
        historyOf(conversation).clear();
        byte[] id = IOUtils.encode(userNameSep + identify(conversation));
        AxolotlNative.deleteConversation(id);
    }

    @Override
    public void save(Conversation conv) {
        byte[] data = serialize(conv).getBytes();

        String partner = identify(conv);
        byte[] id = IOUtils.encode(userNameSep + partner);
        AxolotlNative.storeConversation(id, data);
        Arrays.fill(data, (byte) 0);
    }

    private String serialize(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        return adapter.adapt(conversation).toString();
    }

    /*
     * Known users JSON data:
     *    {"version":1,"users":["name_1", ...., "name_n"]}
     */
    private List<String> parseKnownUsers(String usersJson) {
        try {
            JSONObject jsonObj = new JSONObject(usersJson);
            JSONArray names = jsonObj.getJSONArray("users");
            if (names.length() == 0)
                return null;

            int length = names.length();
            List<String> nameList = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
                nameList.add(names.getString(i));
            return nameList;
        } catch (JSONException e) {
            // Check how to inform user and then restart?
            Log.w(TAG, "JSON exception: " + e);
        }
        return null;
    }
}
