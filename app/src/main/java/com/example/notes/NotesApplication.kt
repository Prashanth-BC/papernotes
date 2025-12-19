package com.example.notes

import android.app.Application
import com.example.notes.data.ObjectBoxStore

class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBoxStore.init(this)
    }
}
