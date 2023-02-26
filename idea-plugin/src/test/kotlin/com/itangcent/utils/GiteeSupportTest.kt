package com.itangcent.utils

import org.junit.Assert
import org.junit.jupiter.api.Test

/**
 * Test case for [GiteeSupport]
 *
 * @author tangcent
 */
internal class GiteeSupportTest {

    @Test
    fun convertUrlFromGithubToGitee() {
        Assert.assertEquals("https://gitee.com/tangcent/easy-api/raw/master/third/markdown.cn.config",
            GiteeSupport.convertUrlFromGithub("https://raw.githubusercontent.com/tangcent/easy-api/master/third/markdown.cn.config"))
        Assert.assertEquals("https://gitee.com/tangcent/easy-api/raw/master/third/swagger.config",
            GiteeSupport.convertUrlFromGithub("https://raw.githubusercontent.com/tangcent/easy-api/master/third/swagger.config"))
    }
}