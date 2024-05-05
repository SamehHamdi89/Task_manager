package org.oauth.social.controller;

import java.io.IOException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.oauth.social.model.EventData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.model.Event;

@Controller
public class CalendarController {

    private static final String APPLICATION_NAME = "Task Manager App";
    private static HttpTransport httpTransport;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static com.google.api.services.calendar.Calendar googleCalendarClient;

    private static SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a");

    GoogleClientSecrets clientSecrets;
    GoogleAuthorizationCodeFlow flow;
    Credential credential;

    @Value("${google.client.client-id}")
    private String clientId;

    @Value("${google.client.client-secret}")
    private String clientSecret;

    @Value("${google.client.redirectUri}")
    private String redirectUri;

    @Value("${google.client.redirectUri.available.slot}")
    private String redirectUriAvailableSlot;

    private Set<Event> events = new HashSet<>();

    private final int START_HOUR = 8;
    private final int START_MIN = 0;
    private final int END_HOUR = 20;
    private final int END_MIN = 0;

    private static boolean isAuthorized = false;

    public void setEvents(Set<Event> events) {
        this.events = events;
    }

    private String authorize(String redirectURL) throws Exception {
        AuthorizationCodeRequestUrl authorizationUrl;
        if (flow == null) {
            Details web = new Details();
            web.setClientId(clientId);
            web.setClientSecret(clientSecret);
            clientSecrets = new GoogleClientSecrets().setWeb(web);
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets,
                    Collections.singleton(CalendarScopes.CALENDAR)).build();
        }
        authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURL);

        isAuthorized = true;

        return authorizationUrl.build();
    }

    @RequestMapping(value = "/calendar", method = RequestMethod.GET)
    public RedirectView redirectToGoogleCalendar(HttpServletRequest request) throws Exception {
        return new RedirectView(authorize(redirectUri));
    }

    @RequestMapping(value = "/calendar", method = RequestMethod.GET, params = "code")
    public String handleOAuth2Callback(@RequestParam(value = "code") String code, Model model) {
        if (isAuthorized) {
            try {
                model.addAttribute("title", "Today's Calendar Events (" + START_HOUR + ":" + START_MIN + " - " + END_HOUR + ":" + END_MIN + ")");
                model.addAttribute("calendarObjects", getTodaysCalendarEvents(code, redirectUri));

            } catch (Exception e) {
                model.addAttribute("calendarObjects", new ArrayList<>());
            }

            return "agenda";
        } else {
            return "/";
        }
    }

    @RequestMapping(value = "/slot/available", method = RequestMethod.GET)
    public RedirectView checkConnectionStatus(HttpServletRequest request, Model model) throws Exception {
        return new RedirectView(authorize(redirectUriAvailableSlot));
    }

    @RequestMapping(value = "/slot/available", method = RequestMethod.GET, params = "code")
    public String handleOAuth2CallbackForSlots(@RequestParam(value = "code") String code, Model model) {
        if (isAuthorized) {
            try {
                List<EventData> calendarEventList = getTodaysCalendarEvents(code, redirectUriAvailableSlot);
                List<EventData> freeCalendarObjects = populateAvailableSlots(calendarEventList);

                model.addAttribute("title", "Today's Available Slots (" + START_HOUR + ":" + START_MIN + " - " + END_HOUR + ":" + END_MIN + ")");
                model.addAttribute("calendarObjects", freeCalendarObjects);

            } catch (Exception e) {
                model.addAttribute("title", e.getMessage());
                model.addAttribute("calendarObjects", new ArrayList<>());
            }

            return "availableslots";
        } else {
            return "/";
        }
    }

    private List<EventData> getTodaysCalendarEvents(String calendarApiCode, String redirectURL) {
        try {
            com.google.api.services.calendar.model.Events eventList;

            LocalDateTime localDateTime = LocalDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault());
            LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
            LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);

            DateTime date1 = new DateTime(Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()));
            DateTime date2 = new DateTime(Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant()));

            TokenResponse response = flow.newTokenRequest(calendarApiCode).setRedirectUri(redirectURL).execute();
            credential = flow.createAndStoreCredential(response, "userID");
            googleCalendarClient = new com.google.api.services.calendar.Calendar.Builder(
                    httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
            Events events = googleCalendarClient.events();
            eventList = events.list("primary").setSingleEvents(true).setTimeMin(date1).setTimeMax(date2).setOrderBy("startTime").execute();

            List<Event> items = eventList.getItems();

            EventData calendarObject;
            List<EventData> calendarObjects = new ArrayList<>();

            for (Event event : items) {
                Date startDateTime = new Date(event.getStart().getDateTime().getValue());
                Date endDateTime = new Date(event.getEnd().getDateTime().getValue());

                long diffInMillies = endDateTime.getTime() - startDateTime.getTime();
                int diffMin = (int) (diffInMillies / (60 * 1000));

                calendarObject = new EventData();

                if (event.getSummary() != null && event.getSummary().length() > 0) {
                    calendarObject.setTitle(event.getSummary());
                } else {
                    calendarObject.setTitle("No Title");
                }

                calendarObject.setStartHour(startDateTime.getHours());
                calendarObject.setStartMin(startDateTime.getMinutes());
                calendarObject.setEndHour(endDateTime.getHours());
                calendarObject.setEndMin(endDateTime.getMinutes());
                calendarObject.setDuration(diffMin);

                calendarObject.setStartEnd(timeFormat.format(startDateTime) + " - " + timeFormat.format(endDateTime));

                calendarObjects.add(calendarObject);
            }

            return calendarObjects;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<EventData> populateAvailableSlots(List<EventData> calendarEventList) {
        int freeSlotStartHour = START_HOUR;
        int freeSlotStartMin = START_MIN;
        int freeSlotEndHour = END_HOUR;
        int freeSlotEndMin = END_MIN;

        EventData freeCalEvent;
        EventData thisCalEvent;
        EventData nextCalEvent;
        List<EventData> freeCalendarObjects = new ArrayList<>();

        for (int count = 0; count < calendarEventList.size() - 1; count++) {
            thisCalEvent = calendarEventList.get(count);

            int thisEventStartHour = thisCalEvent.getStartHour();
            int thisEventStartMin = thisCalEvent.getStartMin();
            int thisEventEndHour = thisCalEvent.getEndHour();
            int thisEventEndMin = thisCalEvent.getEndMin();

            if (count == 0) {
                if ((thisEventStartHour > START_HOUR) || (thisEventStartHour == START_HOUR && thisEventStartMin > START_MIN)) {
                    freeSlotStartHour = START_HOUR;
                    freeSlotStartMin = START_MIN;

                    freeSlotEndHour = thisEventStartHour;
                    freeSlotEndMin = thisEventStartMin;

                    int diffMin = (thisEventStartHour - START_HOUR) * 60 + Math.abs(thisEventStartMin - START_MIN);

                    freeCalEvent = new EventData();
                    freeCalEvent.setTitle("Free Slot 1");
                    freeCalEvent.setStartHour(freeSlotStartHour);
                    freeCalEvent.setStartMin(freeSlotStartMin);
                    freeCalEvent.setEndHour(freeSlotEndHour);
                    freeCalEvent.setEndMin(freeSlotEndMin);
                    freeCalEvent.setDuration(diffMin);

                    freeCalEvent.setStartEnd(freeSlotStartHour + ":" + freeSlotStartMin + " - " + freeSlotEndHour + ":" + freeSlotEndMin);

                    freeCalendarObjects.add(freeCalEvent);
                }
            }

            nextCalEvent = calendarEventList.get(count + 1);

            int nextEventStartHour = nextCalEvent.getStartHour();
            int nextEventStartMin = nextCalEvent.getStartMin();

            freeSlotStartHour = thisEventEndHour;
            freeSlotStartMin = thisEventEndMin;

            freeSlotEndHour = nextEventStartHour;
            freeSlotEndMin = nextEventStartMin;

            int diffMin = (nextEventStartHour - thisEventEndHour - 1) * 60 + Math.abs(nextEventStartMin + thisEventEndMin);
            if (nextEventStartMin == thisEventEndMin) {
                diffMin = (nextEventStartHour - thisEventEndHour) * 60;
            }

            if (diffMin > 0) {
                freeCalEvent = new EventData();
                freeCalEvent.setTitle("Free Slot " + (count + 1));
                freeCalEvent.setStartHour(freeSlotStartHour);
                freeCalEvent.setStartMin(freeSlotStartMin);
                freeCalEvent.setEndHour(freeSlotEndHour);
                freeCalEvent.setEndMin(freeSlotEndMin);
                freeCalEvent.setDuration(diffMin);

                freeCalEvent.setStartEnd(freeSlotStartHour + ":" + freeSlotStartMin + " - " + freeSlotEndHour + ":" + freeSlotEndMin);

                freeCalendarObjects.add(freeCalEvent);
            }
        }

        thisCalEvent = calendarEventList.get(calendarEventList.size() - 1);

        int thisEventEndHour = thisCalEvent.getEndHour();
        int thisEventEndMin = thisCalEvent.getEndMin();

        if ((thisEventEndHour < END_HOUR) || (thisEventEndHour == END_HOUR && thisEventEndMin < END_MIN)) {
            freeSlotStartHour = END_HOUR;
            freeSlotStartMin = END_MIN;

            freeSlotEndHour = thisEventEndHour;
            freeSlotEndMin = thisEventEndMin;

            int diffMin = (END_HOUR - thisEventEndHour - 1) * 60 + Math.abs(thisEventEndMin - END_MIN);

            freeCalEvent = new EventData();
            freeCalEvent.setTitle("Free Slot " + (calendarEventList.size()));
            freeCalEvent.setStartHour(freeSlotStartHour);
            freeCalEvent.setStartMin(freeSlotStartMin);
            freeCalEvent.setEndHour(freeSlotEndHour);
            freeCalEvent.setEndMin(freeSlotEndMin);
            freeCalEvent.setDuration(diffMin);

            freeCalEvent.setStartEnd(freeSlotEndHour + ":" + freeSlotEndMin + " - " + freeSlotStartHour + ":" + freeSlotStartMin);

            freeCalendarObjects.add(freeCalEvent);
        }

        return freeCalendarObjects;
    }

}
