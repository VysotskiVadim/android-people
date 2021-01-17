/*
 * Copyright (C) 2020 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.people.data

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val CURRENT_USER_ID = 0L

interface ChatRepository {
    fun getContacts(): LiveData<List<Contact>>
    fun findContact(id: Long): LiveData<Contact?>
    fun findMessages(id: Long): LiveData<List<Message>>
    fun sendMessage(id: Long, text: String, photoUri: Uri?, photoMimeType: String?)
    fun updateNotification(id: Long)
    fun activateChat(id: Long)
    fun deactivateChat(id: Long)
}

class DefaultChatRepository internal constructor(
    private val notifications: Notifications
) : ChatRepository, CoroutineScope {

    companion object {
        private var instance: DefaultChatRepository? = null

        fun getInstance(context: Context): DefaultChatRepository {
            return instance ?: synchronized(this) {
                instance ?: DefaultChatRepository(
                    AndroidNotifications(context)
                ).also {
                    instance = it
                }
            }
        }
    }

    override val coroutineContext: CoroutineContext = Dispatchers.Main + SupervisorJob()

    private var currentChat: Long = 0L

    private val chats = Contact.CONTACTS.map { contact ->
        contact.id to Chat(contact)
    }.toMap()

    init {
        notifications.initialize()
    }

    @MainThread
    override fun getContacts(): LiveData<List<Contact>> {
        return MutableLiveData<List<Contact>>().apply {
            postValue(Contact.CONTACTS)
        }
    }

    @MainThread
    override fun findContact(id: Long): LiveData<Contact?> {
        return MutableLiveData<Contact>().apply {
            postValue(Contact.CONTACTS.find { it.id == id })
        }
    }

    @MainThread
    override fun findMessages(id: Long): LiveData<List<Message>> {
        val chat = chats.getValue(id)
        return object : LiveData<List<Message>>() {

            private val listener = { messages: List<Message> ->
                postValue(messages)
            }

            override fun onActive() {
                value = chat.messages
                chat.addListener(listener)
            }

            override fun onInactive() {
                chat.removeListener(listener)
            }
        }
    }

    @MainThread
    override fun sendMessage(id: Long, text: String, photoUri: Uri?, photoMimeType: String?) {
        val chat = chats.getValue(id)
        chat.addMessage(Message.Builder().apply {
            sender = CURRENT_USER_ID
            this.text = text
            timestamp = System.currentTimeMillis()
            this.photo = photoUri
            this.photoMimeType = photoMimeType
            this.id = MessagesIds.getNextMessageId()
        }.build())
        launch {
            delay(5000) // The animal is typing...
            chat.contact.reply(text).collect { chatUpdate ->
                when (chatUpdate) {
                    is ChatUpdate.NewMessage -> {
                        chat.addMessage(chatUpdate.message)
                        if (chat.contact.id != currentChat) {
                            notifications.showNotification(chat)
                        }
                    }
                    is ChatUpdate.UpdateMessage -> {
                        chat.updateMessage(chatUpdate.message)
                        // TODO: update notification
                    }
                    is ChatUpdate.RemoveMessage -> {
                        chat.removeMessage(chatUpdate.messageId)
                        // TODO: dismiss notification
                    }
                }
            }
        }
    }

    override fun updateNotification(id: Long) {
        val chat = chats.getValue(id)
        notifications.showNotification(chat)
    }

    override fun activateChat(id: Long) {
        currentChat = id
        notifications.dismissNotification(id)
    }

    override fun deactivateChat(id: Long) {
        if (currentChat == id) {
            currentChat = 0
        }
    }
}
