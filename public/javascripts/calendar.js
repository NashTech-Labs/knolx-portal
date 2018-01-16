var freeSlotTitle = "Book Now!";
var pendingSessionColor = '#f0ad4e';
var scheduledSessionColor = '#31b0d5';
var scheduledMeetupColor = '#8e44ad';
var freeSlotColor = '#27ae60';
var isAdmin = false;

$(function () {

    $('#calendar').fullCalendar({
        loading: function () {
            $("#calendar").css("opacity", "0.6");
        },
        eventAfterAllRender: function () {
            $("#calendar").css("opacity", "1");
        },
        events: function (start, end, timezone, callback) {
            getSessions(start.valueOf(), end.valueOf(), callback)
        },
        eventRender: function (event, element) {
            element.popover({
                html: true,
                container: 'body',
                animation: true,
                delay: 300,
                content: event.data,
                placement: 'bottom',
                trigger: 'manual'
            }).on("mouseenter", function () {
                var _this = this;
                $(this).popover("show");
                var popoverId = $(this).attr("aria-describedby");
                $("#" + popoverId).on("mouseleave", function () {
                    $(_this).popover('hide');
                });
            }).on("mouseleave", function () {
                var _this = this;
                var popoverId = $(this).attr("aria-describedby");
                setTimeout(function () {
                    if (!$("#" + popoverId + ":hover").length) {
                        $(_this).popover("hide");
                    }
                }, 300);
            });
        },
        timezone: 'local',
        eventClick: function (event) {
            if (event.url && !event.url.isEmpty) {
                window.open(event.url, "_self");
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
            end: moment().startOf('month').add(4, 'M')
        },
        viewRender: function (view) {
            $('.fc-day').filter(
                function (index) {
                    return moment($(this).data('date')).isBefore(moment(), 'day')
                }).addClass('fc-other-month');

            $('.fc-day-top').filter(
                function (index) {
                    return moment($(this).data('date')).isBefore(moment(), 'day')
                }).addClass('fc-other-month');
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

                for (var calendarSession = 0; calendarSession < calendarSessions.length; calendarSession++) {

                    events.push({
                        id: calendarSessions[calendarSession].id,
                        title: calendarSessions[calendarSession].topic,
                        start: calendarSessions[calendarSession].date,
                        color: getColor(calendarSessions[calendarSession]),
                        data: getData(calendarSessions[calendarSession]),
                        url: getUrl(calendarSessions[calendarSession], calendarSessionsWithAuthority)
                    });
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

function getColor(calendarSession) {
    if (calendarSession.pending) {
        if (calendarSession.freeSlot) {
            return freeSlotColor;
        } else {
            return pendingSessionColor;
        }
    } else if (calendarSession.meetup) {
        return scheduledMeetupColor;
    } else {
        return scheduledSessionColor;
    }
}

function getData(calendarSession) {
    if (!calendarSession.freeSlot) {
        if (calendarSession.contentAvailable) {
            return "<p>Topic: " + calendarSession.topic
                + "<br>Email: " + calendarSession.email + "</p>"
                + "<br><a href='" + jsRoutes.controllers.SessionsController.shareContent(calendarSession.id).url +
                "' style='text-decoration: none;' target='_blank'><span class='label more-detail-session'>Slide deck & Videos</span></a>";
        } else {
            return "<p>Topic: " + calendarSession.topic
                + "<br>Email: " + calendarSession.email + "</p>";
        }
    }
}

function getUrl(calendarSession, calendarSessionsWithAuthority) {
    if (calendarSession.freeSlot) {
        return jsRoutes.controllers.CalendarController
            .renderCreateSessionByUser(calendarSession.id,
                calendarSession.freeSlot).url;
    } else if (calendarSession.pending) {
        if (calendarSessionsWithAuthority.isAdmin) {
            return jsRoutes.controllers.SessionsController
                .renderScheduleSessionByAdmin(calendarSession.id).url;
        } else if (calendarSessionsWithAuthority.loggedIn
            && calendarSession.email === calendarSessionsWithAuthority.email) {
            return jsRoutes.controllers.CalendarController
                .renderCreateSessionByUser(calendarSession.id,
                    calendarSession.freeSlot).url;
        }
    } else if (calendarSessionsWithAuthority.isAdmin) {
        return jsRoutes.controllers.SessionsController
            .update(calendarSession.id).url;
    }
}
