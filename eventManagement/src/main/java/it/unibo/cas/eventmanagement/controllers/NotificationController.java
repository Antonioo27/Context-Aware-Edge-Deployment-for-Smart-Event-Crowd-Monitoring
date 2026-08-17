package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.enums.UserType;
import it.unibo.cas.eventmanagement.services.NotifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("api/event")
public class NotificationController {
    @Autowired
    private NotifyService notifyService;

    @PostMapping("/notify-{userType}")
    public ResponseEntity<?> getNotifyByUser(@PathVariable String userType) {
        if (userType == null) {
            return ResponseEntity.badRequest().body("userType is null");
        }
        if (Arrays.stream(UserType.values()).noneMatch(u -> u.toString().equalsIgnoreCase(userType))) {
            return ResponseEntity.badRequest().body("Invalid userType");
        }
        return ResponseEntity.ok(notifyService.getNotifyByUser(UserType.valueOf(userType.toUpperCase())));
    }
}