package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/user")
public interface UserApiCall {

    Logger log = LoggerFactory.getLogger(UserApiCall.class);

    @GetExchange("/profile")
    public ApiResponse<UserResponse> getUserProfile(@RequestHeader("Authorization") String jwt);

}
