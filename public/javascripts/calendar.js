$(function () {
    $('#calendar').fullCalendar({
        weekends: false,
        dayClick: function() {
            alert("A day has been clicked");
        }
    });
});