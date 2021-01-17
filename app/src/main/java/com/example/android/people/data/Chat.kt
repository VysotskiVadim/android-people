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

typealias ChatThreadListener = (List<Message>) -> Unit

class Chat(val contact: Contact) {

    private val listeners = mutableListOf<ChatThreadListener>()

    private val _messages = mutableListOf(
        Message(MessagesIds.getNextMessageId(), contact.id, "Send me a message", null, null, System.currentTimeMillis()),
        Message(MessagesIds.getNextMessageId(), contact.id, "I will reply in 5 seconds", null, null, System.currentTimeMillis())
    )
    val messages: List<Message>
        get() = _messages

    fun addListener(listener: ChatThreadListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ChatThreadListener) {
        listeners.remove(listener)
    }

    fun addMessage(message: Message) {
        _messages.add(message)
        onMessagesUpdated()
    }

    fun updateMessage(message: Message) {
        val index = _messages.indexOfFirst { it.id == message.id }
        if (index != -1) {
            _messages[index] = message
            onMessagesUpdated()
        }
    }

    fun removeMessage(messageId: Long) {
        val index = _messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            _messages.removeAt(index)
            onMessagesUpdated()
        }
    }

    private fun onMessagesUpdated() {
        listeners.forEach { listener -> listener(_messages) }
    }
}
