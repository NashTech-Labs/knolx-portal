var freeSlotTitle = "Book Now!";
var pendingSessionColor = '#f0ad4e';
var scheduledSession = '#31b0d5';
var scheduledMeetup = '#8e44ad';
var freeSlotColor = '#27ae60';
var allowedNoOfSessions = 2;

$(function () {

    $('#calendar').fullCalendar({
        events: function (start, end, timezone, callback) {
            getSessions(start.valueOf(), end.valueOf(), callback)
        },
        eventRender: function (event, element) {
            if (event.title === freeSlotTitle || event.color === pendingSessionColor) {
                element.find('.fc-time').hide();
            }
            element.popover({
                html: true,
                container: 'body',
                animation: true,
                delay: 300,
                content: event.data,
                placement: 'bottom',
                trigger: 'hover'
            });
        },
        timezone: 'local',
        eventClick: function (event) {
            if (event.url) {
                window.open(event.url);
                /*openWindowWithPost(event.url, {
                    date: event.start,
                    csrfToken: $('#csrfToken').val()
                });*/
                return false;
            }
        },
        eventDrop: function (event, delta, revertFunc, jsEvent, ui, view) {
            if (moment(event.start).day() === 5) {
                var pulledEvents = $('#calendar').fullCalendar('clientEvents');
                var numberOfEvents = 0;
                var replaced = 0;
                for (var i = 0; i < pulledEvents.length; i++) {
                    if (moment(pulledEvents[i].start).date() === moment(event.start).date()
                        && moment(pulledEvents[i].start).month() === moment(event.start).month()
                        && pulledEvents[i].title === freeSlotTitle) {
                        var freeSlot = {
                            id: moment() + i,
                            title: freeSlotTitle,
                            start: moment(event.start.valueOf()).subtract(delta),
                            color: freeSlotColor,
                            url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(null, event.start._i.valueOf()).url
                        };
                        $('#calendar').fullCalendar('renderEvent', freeSlot);
                        $('#calendar').fullCalendar('removeEvents', pulledEvents[i].id);
                        replaced = 1;
                        numberOfEvents++;
                        updatePendingSession(event.id, event.start.valueOf());
                        break;
                    }
                }
                if(replaced === 0) {
                    revertFunc();
                }
            } else {
                revertFunc();
            }
        }
    });
});

function getSessions(startDate, endDate, callback) {
    jsRoutes.controllers.CalendarController.calendarSessions(startDate, endDate).ajax(
        {
            type: 'GET',
            success: function (calendarSessionsWithAuthority) {
                console.log("data ->" + calendarSessionsWithAuthority.calendarSessions);
                var events = [];
                var calendarSessions = calendarSessionsWithAuthority.calendarSessions;
                for (var i = 0; i < calendarSessions.length; i++) {
                    if (calendarSessions[i].pending) {
                        if (calendarSessionsWithAuthority.isAdmin) {
                            events.push({
                                id: calendarSessions[i].id,
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: pendingSessionColor,
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>",
                                url: jsRoutes.controllers.SessionsController.renderApproveSessionByAdmin(calendarSessions[i].id).url,
                                editable: true
                            });
                        } else if (calendarSessionsWithAuthority.isLoggedIn && calendarSessions[i].email === calendarSessionsWithAuthority.email) {
                            events.push({
                                id: calendarSessions[i].id,
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: pendingSessionColor,
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>",
                                url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(calendarSessions[i].id, calendarSessions[i].date).url,
                                editable: true
                            });
                        } else {
                            events.push({
                                id: calendarSessions[i].id,
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: pendingSessionColor,
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>"
                            });
                        }
                    } else {
                        if (calendarSessions[i].meetup) {
                            events.push({
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: scheduledMeetup,
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>"
                            });
                        } else {
                            events.push({
                                id: calendarSessions[i].id,
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: scheduledSession,
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>"
                            });
                        }
                    }
                }

                var startDay = moment(startDate).set({'hour': 0, 'minute': 0, 'second': 0, 'millisecond': 0});
                var endDay = moment(endDate);
                console.log("Start Date -> " + startDay);
                console.log("End Date -> " + endDay);
                var friday = startDay.clone().day(5);
                while (friday <= endDay) {
                    console.log("friday.toString() ----->" + friday.toString());
                    console.log("friday.valueOf() ----->" + friday.valueOf());

                    var numberOfEvents = 0;

                    for (var i = 0; i < events.length; i++) {
                        if (moment(events[i].start).date() === friday.date() && moment(events[i].start).month() === friday.month()) {
                            numberOfEvents++;
                        }
                    }

                    if (numberOfEvents <= allowedNoOfSessions) {
                        var openSlots = allowedNoOfSessions - numberOfEvents;
                        for (var i = 0; i < openSlots; i++) {
                            events.push({
                                id: moment() + i,
                                title: freeSlotTitle,
                                start: friday.valueOf(),
                                color: freeSlotColor,
                                url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(null, friday.valueOf()).url
                            });
                        }
                    }
                    friday.add(7, 'd');
                }
                callback(events);
            },
            error: function (er) {
                console.log("error ->" + er.responseText);
            }
        }
    )
}

function updatePendingSession(sessionId, sessionDate) {
    jsRoutes.controllers.CalendarController.updatePendingSessionDate(sessionId, sessionDate).ajax(
        {
            type: 'GET',
            success: function (data) {
                console.log("successfully updated the date.");
            },
            error: function(er) {
                console.log("Failed with the error " + er.responseText);
            }
        }
    )
}

function openWindowWithPost(url, data) {
    var form = document.createElement("form");
    form.target = "_blank";
    form.method = "POST";
    form.action = url;
    form.style.display = "none";

    for (var key in data) {
        var input = document.createElement("input");
        input.type = "hidden";
        input.name = key;
        input.value = data[key];
        form.appendChild(input);
    }

    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
}
