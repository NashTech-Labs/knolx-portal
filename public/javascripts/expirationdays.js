function enableDays() {
    $('#days').prop('disabled', false);
}

function disableDays() {
    $('#days').prop('disabled', true);
}

$("#days").bind('keyup mouseup', function () {
    var days = $("#days").val();

    document.getElementById('radio-days').value = days;
});
