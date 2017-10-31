$(function () {

    $('#other-input').hide();

    $('#disableDays').click(function () {
        disableDays();
    });

    $('#radio-days').click(function () {
        enableDays();
    });

    $('#category').change(function(){
        if($(this).val() === "other"){
         $('#other-input').show();
         $('#other').prop("required", true);
        } else {
         $('#other-input').hide();
         $('#other').prop("required", false);
         }
    });
});


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
