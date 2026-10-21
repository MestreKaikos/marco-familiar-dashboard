package com.lito.planify.dashboard.data.repository

import com.lito.planify.dashboard.data.Api
import com.lito.planify.dashboard.data.ShoppingList
import com.lito.planify.dashboard.data.bodyOrThrow
import com.lito.planify.dashboard.data.cache.Cached
import com.lito.planify.dashboard.data.cache.CachedResource
import com.lito.planify.dashboard.data.cache.DiskCache
import kotlinx.coroutines.flow.StateFlow

/**
 * Lista de la compra: Bring! (lo que se añade en la app o por voz con Alexa) que el servidor exporta cada minuto a un
 * JSON en una ruta secreta, junto con las tareas de Todoist «Casa». Solo lectura: se tacha en Bring! o en Todoist.
 */
class ShoppingRepository(private val api: Api, cache: DiskCache, private val url: String) {
    private val resource = CachedResource<ShoppingList>(cache, "shopping", ShoppingList::class.java, MAX_AGE_MS)

    val list: StateFlow<Cached<ShoppingList>?> = resource.data

    fun isStale() = resource.isStale()

    suspend fun refresh(): Result<ShoppingList> = resource.refresh { api.shopping(url).bodyOrThrow() }

    companion object {
        const val MAX_AGE_MS = 2 * 60_000L
    }
}
