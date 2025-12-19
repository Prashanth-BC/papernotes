package com.example.notes.data

import android.content.Context
import com.example.notes.data.MyObjectBox // Generated code
import io.objectbox.BoxStore

object ObjectBoxStore {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        if (!::store.isInitialized) {
            store = MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .build()
        }
    }
}
