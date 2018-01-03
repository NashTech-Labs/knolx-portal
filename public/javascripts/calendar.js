$(function () {
    $('#calendar').fullCalendar({
        events: function(start, end, timezone, callback) {
            console.log("Start date -> " + start.toString());
            console.log("End date -> " + end.toString());
            console.log("End date -> " + end.toDate());
            getSessions(start.valueOf(), end.valueOf(), callback)
        },
        eventRender: function(event, element){
            if(event.title === 'Book Now!' || event.color === '#f0ad4e') {
                element.find('.fc-time').hide();
            }
            element.popover({
                html: true,
                container: 'body',
                animation:true,
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
                for(var i=0 ; i<calendarSessions.length ; i++) {
                    if(calendarSessions[i].pending) {
                        if(calendarSessionsWithAuthority.isAdmin) {
                            events.push({
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: '#f0ad4e',
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>",
                                url: jsRoutes.controllers.SessionsController.renderApproveSessionByAdmin(calendarSessions[i].id).url
                            });
                        } else if(calendarSessionsWithAuthority.isLoggedIn && calendarSessions[i].email === calendarSessionsWithAuthority.email) {
                            events.push({
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: '#f0ad4e',
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>",
                                url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(calendarSessions[i].id, calendarSessions[i].date).url
                            });
                        } else {
                            events.push({
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: '#f0ad4e',
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>"
                            });
                        }} else {
                        if(calendarSessions[i].meetup) {
                            events.push({
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: '#8e44ad',
                                data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>"
                            });
                        } else {
                            events.push({
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: '#31b0d5',
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

                    for(var i=0 ; i<events.length ; i++) {
                        if(moment(events[i].start).date() === friday.date() && moment(events[i].start).month() === friday.month()) {
                            if(friday.date() === 29) {
                                console.log("Title -> " + events[i].title);
                                console.log("date -> " + events[i].start);
                            }
                            numberOfEvents++;
                        }
                    }

                    if(numberOfEvents <= 2) {
                        var openSlots = 2 - numberOfEvents;
                        for(var i=0 ; i < openSlots ; i++) {
                                events.push({
                                    title: 'Book Now!',
                                    start: friday.valueOf(),
                                    color: '#27ae60',
                                    url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(null, friday.valueOf()).url
                                });
                        }
                    }
                    friday.add(7, 'd');
                }
                callback(events);
            },
            error: function(er) {
                console.log("error ->" + er.responseText);
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
