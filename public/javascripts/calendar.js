var freeSlotTitle = "Book Now!";
var pendingSessionColor = '#f0ad4e';
var scheduledSession = '#31b0d5';
var scheduledMeetup = '#8e44ad';
var freeSlotColor = '#27ae60';
var allowedNoOfSessions = 2;
var freeSlotId = 0;
var dragStart = 0;

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
        dayClick: function (date, jsEvent, view) {
            var formattedDate = moment(date).format("YYYY-MM-DDThh:mm").replace("A","T");
            $.confirm({
                title: 'Add Free Slot!',
                content: '' +
                '<form action="" class="formName">' +
                '<div class="form-group">' +
                '<input type="datetime-local" id="free-slot" value="' + formattedDate + '" class="update-field login-second" />' +
                '</div>' +
                '</form>',
                buttons: {
                    formSubmit: {
                        text: 'Add',
                        btnClass: 'btn-blue',
                        action: function () {
                            var freeSlot = this.$content.find('#free-slot').val();
                            if (!freeSlot) {
                                $.alert('Date must not be empty');
                                return false;
                            }
                            jsRoutes.controllers.CalendarController.insertFreeSlot(null, freeSlot).ajax(
                                {
                                    type: 'GET',
                                    processData: false,
                                    beforeSend: function (request) {
                                        var csrfToken = document.getElementById('csrfToken').value;

                                        return request.setRequestHeader('CSRF-Token', csrfToken);
                                    },
                                    success: function (data) {
                                        $("#calendar").fullCalendar('refetchEvents');
                                        console.log("Successfully inserted the free slot with data  -----> " + data);
                                    },
                                    error: function (er) {
                                        console.log("Error with responseText -----> " + er.responseText);
                                    }
                                }
                            )
                        }
                    },
                    cancel: function () {
                        //close
                    }
                },
                onContentReady: function () {
                    var jc = this;
                    this.$content.find('form').on('submit', function (e) {
                        e.preventDefault();
                        jc.$$formSubmit.trigger('click'); // reference the button and click it
                        console.log("Akshansh1234");
                        //$("#recommendation-form").submit();
                    });
                }
            });
            //insertFreeSlot(date);
        },
        eventDragStart: function(event, jsEvent, ui, view) {
            dragStart = 1;
        },
        eventDrop: function (event, delta, revertFunc, jsEvent, ui, view) {
            dragStart = 0;
            if (moment(event.start).day() === 5) {
                var pulledEvents = $('#calendar').fullCalendar('clientEvents');
                var numberOfEvents = 0;
                var replaced = 0;
                for (var i = 0; i < pulledEvents.length; i++) {
                    if (moment(pulledEvents[i].start).date() === moment(event.start).date()
                        && moment(pulledEvents[i].start).month() === moment(event.start).month()
                        && pulledEvents[i].title === freeSlotTitle) {
                        var freeSlot = {
                            id: ++freeSlotId,
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
        },
        validRange: {
            start: moment().startOf('month'),
            end: moment().startOf('month').add(3, 'M')
        },
        droppable: true,
        eventDragStop: function( event, jsEvent, ui, view ) {

            if(isEventOverDiv(jsEvent.clientX, jsEvent.clientY)) {
                $('#calendar').fullCalendar('removeEvents', event.id);
                var el = $( "<div class='fc-event'>" ).appendTo( '#box' ).text( event.title );
                /*el.draggable({
                    zIndex: 999,
                    revert: true,
                    revertDuration: 0
                });*/
                el.attr('draggable', 'true');
                el.dataTransfer.setData('zIndex', '999');
                el.dataTransfer.setData('revert', 'true');
                el.dataTransfer.setData('revertDuration', '0');
                el.data('event', {
                    id: event.id,
                    title: event.title,
                    start: event.start,
                    color: event.color,
                    data: event.data,
                    url: event.url,
                    editable: true
                });
            }
        },
        drop: function() {
            console.log("Dropping event");
        }
    });

    var boundingBox = $("#box").offset();
    boundingBox.right = $("#box").width() + boundingBox.left;
    boundingBox.bottom = $("#box").width() + boundingBox.top;

    var isEventOverDiv = function (x, y) {
            console.log("Moving");
                if (x < boundingBox.right &&
                    x > boundingBox.left &&
                    y < boundingBox.bottom &&
                    y > boundingBox.top) {
                    //$("#calendar").fullCalendar('next');
                    return true;
                } else {
                    console.log("Bounding box right -----> " + boundingBox.right);
                    console.log("Bounding box left -----> " + boundingBox.left);
                    console.log("e pageX -----> " + x);
                    console.log("e pageY -----> " + y);
                    return false;
                }
    }
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
                        if(calendarSessions[i].freeSlot) {
                            events.push({
                                id: calendarSessions[i].id,
                                title: calendarSessions[i].topic,
                                start: calendarSessions[i].date,
                                color: freeSlotColor,
                                url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(calendarSessions[i].id, calendarSessions[i].date.valueOf()).url
                            });
                        }
                        else if (calendarSessionsWithAuthority.isAdmin) {
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
                        if (calendarSessionsWithAuthority.isAdmin) {
                            if (calendarSessions[i].meetup) {
                                events.push({
                                    id: calendarSessions[i].id,
                                    title: calendarSessions[i].topic,
                                    start: calendarSessions[i].date,
                                    color: scheduledMeetup,
                                    data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>",
                                    url: jsRoutes.controllers.SessionsController.update(calendarSessions[i].id).url
                                });
                            } else {
                                events.push({
                                    id: calendarSessions[i].id,
                                    title: calendarSessions[i].topic,
                                    start: calendarSessions[i].date,
                                    color: scheduledSession,
                                    data: "<p>Topic: " + calendarSessions[i].topic + "<br>Email: " + calendarSessions[i].email + "</p>",
                                    url: jsRoutes.controllers.SessionsController.update(calendarSessions[i].id).url
                                });
                            }
                        } else {
                            if (calendarSessions[i].meetup) {
                                events.push({
                                    id: calendarSessions[i].id,
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
                }

                /*var startDay = moment(startDate).set({'hour': 0, 'minute': 0, 'second': 0, 'millisecond': 0});
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
                                id: ++freeSlotId,
                                title: freeSlotTitle,
                                start: friday.valueOf(),
                                color: freeSlotColor,
                                url: jsRoutes.controllers.CalendarController.renderCreateSessionByUser(null, friday.valueOf()).url
                            });
                        }
                    }
                    friday.add(7, 'd');
                }*/
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

function akshansh(event) {
    event.preventDefault();
    console.log("Akshansh is being dropped in akshansh");
}

function insertFreeSlot(date) {
    jsRoutes.controllers.CalendarController.insertFreeSlot(date.valueOf()).ajax(
        {
            type: 'GET',
            success: function(data) {
                console.log("Free slot was inserted successfully with data ----> " + data);
            },
            error: function(er) {
                console.log("Error occured ----> " + er.responseText);
            }
        }
    )
}