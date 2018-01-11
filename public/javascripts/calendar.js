var freeSlotTitle = "Book Now!";
var pendingSessionColor = '#f0ad4e';
var scheduledSessionColor = '#31b0d5';
var scheduledMeetupColor = '#8e44ad';
var freeSlotColor = '#27ae60';
var isAdmin = false;

$(function () {

    $('#calendar').fullCalendar({
        events: function (start, end, timezone, callback) {
            getSessions(start.valueOf(), end.valueOf(), callback)
        },
        eventRender: function (event, element) {
            if (event.title === freeSlotTitle) {
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
                return false;
            }
        },
        dayClick: function (date, jsEvent, view) {
            if (isAdmin) {
                var formattedDate = moment(date).format("YYYY-MM-DDThh:mm").replace("A", "T");
                $.confirm({
                    title: 'Add Free Slot!',
                    content: '' +
                    '<form action="" class="formName">' +
                    '<div class="form-group">' +
                    '<input type="datetime-local" id="free-slot" value="' + formattedDate + '" class="update-field login-second"/>' +
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
                                jsRoutes.controllers.CalendarController.insertFreeSlot(freeSlot).ajax(
                                    {
                                        type: 'GET',
                                        processData: false,
                                        beforeSend: function (request) {
                                            var csrfToken = document.getElementById('csrfToken').value;

                                            return request.setRequestHeader('CSRF-Token', csrfToken);
                                        },
                                        success: function (data) {
                                            $("#calendar").fullCalendar('refetchEvents');
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
                            jc.$$formSubmit.trigger('click');
                        });
                    }
                });
            }
        },
        validRange: {
            start: moment().startOf('month'),
            end: moment().startOf('month').add(3, 'M')
        }
    });

    $("#delete-free-slot").click(function () {
        var id = $("#sessionId").val();
        deleteFreeSlot(id);
    });
});

function getSessions(startDate, endDate, callback) {
    jsRoutes.controllers.CalendarController.calendarSessions(startDate, endDate).ajax(
        {
            type: 'GET',
            success: function (calendarSessionsWithAuthority) {
                isAdmin = calendarSessionsWithAuthority.isAdmin;
                var events = [];
                var calendarSessions = calendarSessionsWithAuthority.calendarSessions;

                for (var calenderSession = 0; calenderSession < calendarSessions.length; calenderSession++) {
                    if (calendarSessions[calenderSession].pending) {
                        if (calendarSessions[calenderSession].freeSlot) {
                            events.push({
                                id: calendarSessions[calenderSession].id,
                                title: calendarSessions[calenderSession].topic,
                                start: calendarSessions[calenderSession].date,
                                color: freeSlotColor,
                                url: jsRoutes.controllers.CalendarController
                                    .renderCreateSessionByUser(calendarSessions[calenderSession].id,
                                        calendarSessions[calenderSession].freeSlot).url
                            });
                        } else if (calendarSessionsWithAuthority.isAdmin) {
                            events.push({
                                id: calendarSessions[calenderSession].id,
                                title: calendarSessions[calenderSession].topic,
                                start: calendarSessions[calenderSession].date,
                                color: pendingSessionColor,
                                data: "<p>Topic: " + calendarSessions[calenderSession].topic + "<br>Email: "
                                + calendarSessions[calenderSession].email + "</p>",
                                url: jsRoutes.controllers.SessionsController
                                    .renderApproveSessionByAdmin(calendarSessions[calenderSession].id).url
                            });
                        } else if (!calendarSessionsWithAuthority.isLoggedIn
                            && calendarSessions[calenderSession].email === calendarSessionsWithAuthority.email) {
                            events.push({
                                id: calendarSessions[calenderSession].id,
                                title: calendarSessions[calenderSession].topic,
                                start: calendarSessions[calenderSession].date,
                                color: pendingSessionColor,
                                data: "<p>Topic: " + calendarSessions[calenderSession].topic + "<br>Email: "
                                + calendarSessions[calenderSession].email + "</p>",
                                url: jsRoutes.controllers.CalendarController
                                    .renderCreateSessionByUser(calendarSessions[calenderSession].id,
                                        calendarSessions[calenderSession].freeSlot).url
                            });
                        } else {
                            events.push({
                                id: calendarSessions[calenderSession].id,
                                title: calendarSessions[calenderSession].topic,
                                start: calendarSessions[calenderSession].date,
                                color: pendingSessionColor,
                                data: "<p>Topic: " + calendarSessions[calenderSession].topic
                                + "<br>Email: " + calendarSessions[calenderSession].email + "</p>"
                            });
                        }
                    } else {
                        if (calendarSessionsWithAuthority.isAdmin) {
                            if (calendarSessions[calenderSession].meetup) {
                                events.push({
                                    id: calendarSessions[calenderSession].id,
                                    title: calendarSessions[calenderSession].topic,
                                    start: calendarSessions[calenderSession].date,
                                    color: scheduledMeetupColor,
                                    data: "<p>Topic: " + calendarSessions[calenderSession].topic
                                    + "<br>Email: " + calendarSessions[calenderSession].email + "</p>",
                                    url: jsRoutes.controllers.SessionsController
                                        .update(calendarSessions[calenderSession].id).url
                                });
                            } else {
                                events.push({
                                    id: calendarSessions[calenderSession].id,
                                    title: calendarSessions[calenderSession].topic,
                                    start: calendarSessions[calenderSession].date,
                                    color: scheduledSessionColor,
                                    data: "<p>Topic: " + calendarSessions[calenderSession].topic
                                    + "<br>Email: " + calendarSessions[calenderSession].email + "</p>",
                                    url: jsRoutes.controllers.SessionsController
                                        .update(calendarSessions[calenderSession].id).url
                                });
                            }
                        } else {
                            if (calendarSessions[calenderSession].meetup) {
                                events.push({
                                    id: calendarSessions[calenderSession].id,
                                    title: calendarSessions[calenderSession].topic,
                                    start: calendarSessions[calenderSession].date,
                                    color: scheduledMeetupColor,
                                    data: "<p>Topic: " + calendarSessions[calenderSession].topic
                                    + "<br>Email: " + calendarSessions[calenderSession].email + "</p>"
                                });
                            } else {
                                events.push({
                                    id: calendarSessions[calenderSession].id,
                                    title: calendarSessions[calenderSession].topic,
                                    start: calendarSessions[calenderSession].date,
                                    color: scheduledSessionColor,
                                    data: "<p>Topic: " + calendarSessions[calenderSession].topic
                                    + "<br>Email: " + calendarSessions[calenderSession].email + "</p>"
                                });
                            }
                        }
                    }
                }
                callback(events);
            },
            error: function (er) {
                console.log("error ->" + er.responseText);
            }
        }
    )
}

function deleteFreeSlot(id) {
    var form = document.createElement("form");

    form.method = "POST";
    form.action = jsRoutes.controllers.CalendarController.deleteFreeSlot(id).url;
    form.style.display = "none";

    var csrfToken = $("#csrfToken").val();

    var input = document.createElement("input");
    input.type = "hidden";
    input.value = csrfToken;
    input.id = "csrfToken";
    input.name = "csrfToken";
    form.appendChild(input);

    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
}
