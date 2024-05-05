package org.oauth.social.util;

import org.oauth.social.model.EventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {
    @Autowired
    private EventService eventService;

    @PostMapping("/CalendarObj")
    public void addEvent(@RequestBody EventData CO) {
        eventService.addEvent(CO);
    }
}
