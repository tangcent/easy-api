package com.itangcent.custom;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring parity fixture for the Custom framework reference ruleset.
 * Uses single-path mappings and explicit composed @*Mapping annotations
 * so the Custom+ruleset output is structurally comparable to the built-in
 * Spring exporter output.
 */
@RestController
@RequestMapping("/parity")
public class SpringParityCtrl {

    /**
     * get user by id
     *
     * @param id user id
     */
    @GetMapping("/users/{id}")
    public Result<UserInfo> getUser(@PathVariable("id") Long id) {
        return Result.success(new UserInfo());
    }

    /**
     * create user
     */
    @PostMapping("/users")
    public Result<UserInfo> createUser(@RequestBody UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * search users
     *
     * @param name user name
     * @param age user age
     */
    @GetMapping("/users")
    public Result<UserInfo> searchUsers(
            @RequestParam("name") String name,
            @RequestParam(value = "age", required = false) Integer age) {
        return Result.success(new UserInfo());
    }

    /**
     * get user by header
     *
     * @param token auth token
     */
    @GetMapping("/by-header")
    public Result<UserInfo> getByHeader(@RequestHeader("X-Token") String token) {
        return Result.success(new UserInfo());
    }

    /**
     * get user by cookie
     *
     * @param sessionId session id
     */
    @GetMapping("/by-cookie")
    public Result<UserInfo> getByCookie(@CookieValue("sessionId") String sessionId) {
        return Result.success(new UserInfo());
    }
}
