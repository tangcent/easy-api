package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.kit.sub
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.utils.Storage.Companion.NULL

@Singleton
@ScriptTypeName("session")
class SessionStorage : AbstractStorage() {

    @Inject
    private val kv = KV<String, Any?>()

    override fun get(group: String?, name: String?): Any? {
        return kv.sub(group ?: NULL)[name]
    }

    override fun set(group: String?, name: String?, value: Any?) {
        kv.sub(group ?: NULL)[name ?: NULL] = value
    }

    override fun remove(group: String?, name: String) {
        kv.sub(group ?: NULL).remove(name)
    }

    override fun keys(group: String?): Array<Any?> {
        return kv.sub(group ?: NULL).keys.toTypedArray()
    }

    override fun clear(group: String?) {
        kv.remove(group)
    }
}
