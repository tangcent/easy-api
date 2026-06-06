package com.itangcent.easyapi.exporter.hoppscotch

import com.itangcent.easyapi.rule.RuleKeys

/**
 * Aliases for Hoppscotch-specific rule keys defined in [RuleKeys].
 *
 * These rule keys allow users to customize Hoppscotch export behavior via
 * the rule engine (e.g., in `.easy.api.yml` files):
 *
 * | Rule Key | Purpose |
 * |----------|---------|
 * | `hopp.prerequest` | Pre-request script for a method |
 * | `hopp.class.prerequest` | Pre-request script for all methods in a class |
 * | `hopp.collection.prerequest` | Pre-request script for the entire collection |
 * | `hopp.test` | Test script for a method |
 * | `hopp.class.test` | Test script for all methods in a class |
 * | `hopp.collection.test` | Test script for the entire collection |
 * | `hopp.host` | Base URL override for endpoints |
 * | `hopp.format.after` | Post-format hook |
 *
 * @see RuleKeys for the canonical key definitions
 * @see HoppscotchFormatter for how these keys are resolved
 */
object HoppscotchRuleKeys {
    val PREREQUEST = RuleKeys.HOPP_PREREQUEST
    val CLASS_PREREQUEST = RuleKeys.HOPP_CLASS_PREREQUEST
    val COLLECTION_PREREQUEST = RuleKeys.HOPP_COLLECTION_PREREQUEST
    val TEST = RuleKeys.HOPP_TEST
    val CLASS_TEST = RuleKeys.HOPP_CLASS_TEST
    val COLLECTION_TEST = RuleKeys.HOPP_COLLECTION_TEST
    val HOST = RuleKeys.HOPP_HOST
    val FORMAT_AFTER = RuleKeys.HOPP_FORMAT_AFTER
}
