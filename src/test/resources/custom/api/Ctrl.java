package com.itangcent.custom;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;

/**
 * Custom-annotated controller fixture for the Custom framework.
 * Uses a proprietary @MyApi / @MyEndpoint annotation set so each
 * custom.* rule key can be exercised independently without Spring.
 */
@com.itangcent.custom.annotation.MyApi
public class Ctrl {

    /**
     * greet the user
     */
    @com.itangcent.custom.annotation.MyEndpoint
    public Result<UserInfo> greet() {
        return Result.success(new UserInfo());
    }

    /**
     * get user by id
     *
     * @param id user id
     */
    @com.itangcent.custom.annotation.MyEndpoint
    public Result<UserInfo> get(Long id) {
        return Result.success(new UserInfo());
    }
}
