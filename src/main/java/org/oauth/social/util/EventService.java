package org.oauth.social.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.oauth.social.model.EventData;

@Service
public class EventService {
    @Autowired
    private EventRepository eventRepository;

    public void addEvent(EventData cO) {
        eventRepository.save(cO);
    }

    // Other service methods if needed
}
