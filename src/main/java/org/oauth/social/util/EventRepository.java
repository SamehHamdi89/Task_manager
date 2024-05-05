package org.oauth.social.util;

import org.oauth.social.model.EventData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventData, Long> {
    
}
