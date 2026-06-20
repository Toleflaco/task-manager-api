package com.mtole.taskmanager.activity;


import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("dev/activity")
@Profile("dev")
public class ActivityController {
    private final ActivityEventRepository activityEventRepository;

    public ActivityController(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    @GetMapping
    public List<ActivityEvent> listAll() {
        return activityEventRepository.findAll();
    }
}
