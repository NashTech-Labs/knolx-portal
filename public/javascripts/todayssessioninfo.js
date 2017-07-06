function opener(value) {
    var details = JSON.parse(value);
    $('#session-topic').html(details.topic);
    $('#author').html(details.author);
    $('#session').html(details.session);
    $('#scheduled').html(details.scheduled);
    $('#expire').html(details.expire);
    $('#sessiontype').html(details.sessiontype);
    $('#session-detail-info').modal('show');
}

function expire() {
    $('#feedback-info-modal-header').css('background-color', '#a8a8a8');
    $('#session-modal-footer-btn').css('background-color', '#a8a8a8');
}
