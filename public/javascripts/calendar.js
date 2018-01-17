var freeSlotTitle = "Book Now!";
var pendingSessionColor = '#f0ad4e';
var scheduledSessionColor = '#31b0d5';
var scheduledMeetupColor = '#8e44ad';
var freeSlotColor = '#27ae60';
var notificationColor = '#d9534f';
var isAdmin = false;

window.onbeforeunload = function () {
    sessionStorage.removeItem("recommendationId");
};

$(function () {

    $('#calendar').fullCalendar({
        loading: function () {
            $("#calendar").css("opacity", "0.6");
            $("#loader").show();
        },
        eventAfterAllRender: function () {
            $("#calendar").css("opacity", "1");
            $("#loader").hide();
        },
        events: function (start, end, timezone, callback) {
            getSessions(start.valueOf(), end.valueOf(), callback)
        },
        eventRender: function (event, element) {
            if (event.notification) {
                $(element).find(".fc-time").hide();
                if(isAdmin) {
                    $(element).addClass("pointer-cursor");
                }
            }
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
            if (event.notification && isAdmin) {
                deleteSlot(event.id);
            }
            if (event.url && !event.url.isEmpty) {
                window.open(event.url, "_self");
                return false;
            }
        },
        dayClick: function (date, jsEvent, view) {
            if (isAdmin) {
                var formattedDate = moment(date).format("YYYY-MM-DDThh:mm").replace("A", "T");
                $.confirm({
                    title: 'Add Free Slot/Notification',
                    content: '' +
                    '<form action="" class="formName">' +
                    '<div class="form-group">' +
                    '<input type="datetime-local" id="slot-date" value="' + formattedDate + '" class="update-field login-second"/>' +
                    '<input type="text" id="slot-name" value="Free Slot" class="update-field login-second" placeholder="Topic"/>' +
                    '<label class="checkbox-outer">' +
                    '<input type="checkbox" id="is-notification" class="custom-checkbox"/>' +
                    '<span class="label_text"></span>' +
                    '<p class="checkbox-text">Is it a notification?</p>' +
                    '</label>' +
                    '</div>' +
                    '</form>',
                    buttons: {
                        formSubmit: {
                            text: 'Add',
                            btnClass: 'btn-blue',
                            action: function () {

                                var slotName = $("#slot-name").val();
                                var slotDate = $("#slot-date").val();
                                var isNotification = $("#is-notification").is(":checked");

                                if (!slotName) {
                                    $.alert('Slot topic must not be empty');
                                    return false;
                                }
                                if (!slotDate) {
                                    $.alert('Slot date must not be empty');
                                    return false;
                                }

                                var formData = new FormData();
                                formData.append("slotName", slotName);
                                formData.append("date", slotDate);
                                formData.append("isNotification", isNotification);

                                jsRoutes.controllers.CalendarController.insertSlot().ajax(
                                    {
                                        type: 'POST',
                                        contentType: false,
                                        processData: false,
                                        data: formData,
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
                        $("#is-notification").change(function () {
                            console.log("Getting here");
                            if ($("#slot-name").val().length > 0) {
                                $("#slot-name").val('');
                            } else {
                                $("#slot-name").val('Free Slot');
                            }
                        });
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

    var recommendationId = sessionStorage.getItem("recommendationId");

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
                        url: getUrl(calendarSessions[calendarSession], calendarSessionsWithAuthority, recommendationId),
                        notification: calendarSessions[calendarSession].notification
                    });
                }
                callback(events);
            },
            error: function (er) {
                console.log("Error ->" + er.responseText);
            }
        }
    )
}

function deleteFreeSlot(id) {
    var form = document.createElement("form");

    form.method = "POST";
    form.action = jsRoutes.controllers.CalendarController.deleteSlot(id).url;
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
    if (calendarSession.notification) {
        return notificationColor;
    } else if (calendarSession.pending) {
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
    if (!calendarSession.freeSlot && !calendarSession.notification) {
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

function getUrl(calendarSession, calendarSessionsWithAuthority, recommendationId) {
    if(calendarSession.freeSlot) {
        return jsRoutes.controllers.CalendarController
            .renderCreateSessionByUser(calendarSession.id,
                recommendationId,
                calendarSession.freeSlot).url;
    } else if (calendarSession.pending) {
        if (calendarSessionsWithAuthority.isAdmin) {
            return jsRoutes.controllers.SessionsController
                .renderScheduleSessionByAdmin(calendarSession.id).url;
        } else if (calendarSessionsWithAuthority.loggedIn
            && calendarSession.email === calendarSessionsWithAuthority.email) {
            return jsRoutes.controllers.CalendarController
                .renderCreateSessionByUser(calendarSession.id,
                    recommendationId,
                    calendarSession.freeSlot).url;
        }
    } else if (calendarSessionsWithAuthority.isAdmin && !calendarSession.notification) {
        return jsRoutes.controllers.SessionsController
            .update(calendarSession.id).url;
    }
}

function deleteSlot(slotId) {
    $.confirm({
        title: 'Delete Slot',
        buttons: {
            formSubmit: {
                text: 'Delete',
                btnClass: 'btn-danger',
                action: function () {

                    jsRoutes.controllers.CalendarController.deleteSlot(slotId).ajax(
                        {
                            type: 'POST',
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