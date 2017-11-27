$(document).on('submit', 'form.customForm', function () {
    $(".loader-outer").html('<div class="loader"></div>')
});

$(document).ready(function () {
    $('[data-toggle="tooltip"]').tooltip();
});

function collapseManageSubmenu() {
    $('#manageSubmenu').collapse('toggle');
}
