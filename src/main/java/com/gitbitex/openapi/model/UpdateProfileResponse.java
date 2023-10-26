package com.gitbitex.openapi.model;

import com.gitbitex.marketdata.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class UpdateProfileResponse extends ResponseEntity<User> {

    public UpdateProfileResponse(User body) {
        super(body, HttpStatus.OK); // Set the desired HTTP status, e.g., OK (200)
    }


}
