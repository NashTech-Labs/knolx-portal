$(function () {
    $('#calendar').fullCalendar({
        events: function(start, end, timezone, callback) {
            console.log("Start date -> " + start.toString());
            console.log("End date -> " + end.toString());
            getSessions(start.valueOf(), end.valueOf(), callback)
        },
        eventRender: function(event, element){
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
                return false;
            }
        }
    });
});

function getSessions(startDate, endDate, callback) {
    jsRoutes.controllers.CalendarController.calendarSessions(startDate, endDate).ajax(
        {
            type: 'GET',
            success: function (data) {
                console.log("data ->" + data);
                var events = [];
                for(var i=0 ; i<data.length ; i++) {
                    events.push({
                        title: data[i].topic,
                        start: data[i].date,
                        color: '#31b0d5',
                        data: "<p>Topic: " + data[i].topic + "<br>Email: " + data[i].email + "</p>",
                        url: 'knolx.knoldus.com'
                    });
                }

                /*var friday = moment()
                    .startOf('month')
                    .day("Friday");*/
                var startDay = moment(startDate);
                var endDay = moment(endDate);
                console.log("Start Date -> " + startDay);
                console.log("End Date -> " + endDay);
                var friday = startDay.clone().day(5);
                    /*.startOf('month')
                    .day("Friday");*/
                //if (friday.date() > 7) friday.add(7, 'd');
                //var month = friday.month();
                while (friday <= endDay) {
                    console.log(friday.toString());
                    console.log(friday.valueOf());

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
                                url: 'knolx.knoldus.com'
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

function dummy() {
    console.log("This is getting called");
}