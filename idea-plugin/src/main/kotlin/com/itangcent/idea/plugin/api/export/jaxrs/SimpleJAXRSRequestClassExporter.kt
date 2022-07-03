package com.itangcent.idea.plugin.api.export.jaxrs

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.stream
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.condition.ConditionOnDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.ApiHelper
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.DocHandle
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import kotlin.reflect.KClass

/**
 * only parse name
 */
@Singleton
@ConditionOnSimple
@ConditionOnClass(JAXRSClassName.PATH_ANNOTATION)
@ConditionOnDoc("request")
@ConditionOnSetting("jaxrsEnable")
open class SimpleJAXRSRequestClassExporter : ClassExporter {

    @Inject
    protected lateinit var annotationHelper: AnnotationHelper

    @Inject
    protected lateinit var jvmClassHelper: JvmClassHelper

    @Inject
    protected lateinit var JAXRSBaseAnnotationParser: JAXRSBaseAnnotationParser

    @Inject
    protected lateinit var classApiExporterHelper: ClassApiExporterHelper

    override fun support(docType: KClass<*>): Boolean {
        return docType == Request::class
    }

    @Inject
    private val logger: Logger? = null

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    protected var apiHelper: ApiHelper? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (cls !is PsiClass) {
            return false
        }
        val clsQualifiedName = actionContext.callInReadUI { cls.qualifiedName }
        try {
            when {
                !JAXRSBaseAnnotationParser.hasApi(cls) -> {
                    return false
                }
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class: $clsQualifiedName")
                    return true
                }
                else -> {
                    logger!!.info("search api from: $clsQualifiedName")
                    classApiExporterHelper.foreachPsiMethod(cls) { method ->
                        exportMethodApi(cls, method, docHandle)
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        }
        return true
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(psiClass: PsiClass, method: PsiMethod, docHandle: DocHandle) {

        actionContext!!.checkStatus()
        if (!JAXRSBaseAnnotationParser.isApi(method)) {
            return
        }

        val request = Request()
        request.resource = PsiMethodResource(method, psiClass)
        request.name = apiHelper!!.nameOfApi(method)
        docHandle(request)
    }
}