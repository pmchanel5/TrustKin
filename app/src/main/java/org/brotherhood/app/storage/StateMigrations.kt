package org.brotherhood.app.storage

import org.brotherhood.app.model.AppState

object StateMigrations {
    const val CURRENT_SCHEMA = 2

    fun migrate(input: AppState): AppState {
        require(input.schemaVersion in 1..CURRENT_SCHEMA) { "Schema archivio non supportato" }
        if (input.schemaVersion == CURRENT_SCHEMA) return input
        return input.copy(
            schemaVersion = CURRENT_SCHEMA,
            contacts = input.contacts.map {
                it.copy(
                    torOnion = "",
                    torPort = 80,
                    endpointRevision = 1,
                    torEndpointRevoked = false,
                )
            },
        )
    }
}
