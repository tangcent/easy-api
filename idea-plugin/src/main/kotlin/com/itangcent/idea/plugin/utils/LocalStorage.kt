package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.KV
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.idea.plugin.utils.Storage.Companion.DEFAULT_GROUP
import com.itangcent.intellij.file.LocalFileRepository

@Singleton
@ScriptTypeName("localStorage")
class LocalStorage : AbstractStorage() {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private var dbBeanBinderFactory: DbBeanBinderFactory<KV<Any?, Any?>>? = null

    private fun getDbBeanBinderFactory(): DbBeanBinderFactory<KV<Any?, Any?>> {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.local.storage.v1.1.db").path)
                { KV.create<Any?, Any?>() }
            }
        }
        return this.dbBeanBinderFactory!!
    }

    override fun get(group: String?, name: String?): Any? {
        return getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
                .tryRead()?.get(name)
    }

    override fun set(group: String?, name: String?, value: Any?) {
        val beanBinder = getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
        val kv = beanBinder.read()
        if (value == null) {
            kv.remove(name)
        } else {
            kv[name] = value
        }
        beanBinder.save(kv)
    }

    override fun remove(group: String?, name: String) {
        val beanBinder = getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
        val kv = beanBinder.tryRead() ?: return
        kv.remove(name)
        beanBinder.save(kv)
    }

    override fun keys(group: String?): Array<Any?> {
        return getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
                .tryRead()
                ?.keys
                ?.toTypedArray()
                ?: emptyArray()
    }


    override fun clear(group: String?) {
        getDbBeanBinderFactory().deleteBinder(group ?: DEFAULT_GROUP)
    }

}
