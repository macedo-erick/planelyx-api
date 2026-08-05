package com.planelyx.api.web;

import com.planelyx.api.dto.MeRequest;
import com.planelyx.api.dto.MeResponse;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own profile — never anyone else's.
 *
 * There is no {@code /users/{id}} counterpart on purpose: the id always comes from the token's
 * {@code sub} via {@link CurrentUser}, the same claim every other resource is scoped by, so
 * there is no id a caller could substitute.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserProfileService userProfileService;
    private final CurrentUser currentUser;

    @GetMapping
    public MeResponse find() {
        return userProfileService.find(currentUser.ownerId());
    }

    @PutMapping
    public MeResponse update(@Valid @RequestBody MeRequest request) {
        return userProfileService.update(currentUser.ownerId(), request);
    }
}
