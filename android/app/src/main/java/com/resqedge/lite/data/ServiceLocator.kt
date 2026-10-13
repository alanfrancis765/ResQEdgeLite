package com.resqedge.lite.data

import android.content.Context

object ServiceLocator {
    @Volatile private var store: PacketStore? = null

    fun store(context: Context): PacketStore = store ?: synchronized(this) {
        store ?: RoomPacketStore(AppDatabase.get(context).packetDao()).also { store = it }
    }

    fun ingestor(context: Context) = Ingestor(store(context))
}
